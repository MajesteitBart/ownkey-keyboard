/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.florisboard.lib.prediction.ngram

import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * A scored vocabulary word returned by [NgramModel] queries. [logProb] is a natural-log probability.
 */
data class NgramCandidate(val wordId: Int, val logProb: Float)

/**
 * Read-only static trigram language model (pruned interpolated Kneser-Ney stored as a backoff model) in the
 * Ownkey n-gram binary format, versions 1 and 2 (see tools/lm/FORMAT.md). Pure Kotlin/JVM, no Android APIs.
 *
 * Word ids: `0` is the sentence-start context token `<s>` ([BOS_ID]); `1 until vocabularySize` are lower-case
 * words sorted by their folded form (no diacritics, no apostrophes), so every folded prefix maps to one contiguous
 * id range. Unknown words have id [UNKNOWN_ID] (`-1`).
 *
 * Context arguments: `prev1Id` is the word right before the predicted word and `prev2Id` the one before that.
 * Pass [BOS_ID] as `prev1Id` at the start of a sentence (then `prev2Id` is ignored), [BOS_ID] as `prev2Id` for the
 * second word of a sentence, and [UNKNOWN_ID] for unknown or missing words (an unknown `prev1Id` falls back to
 * unigram probabilities).
 *
 * Word validity ([isValid], version 2): whether a vocabulary word is really a word (in a curated word list, written
 * with capitals or frequent enough to be intentional) rather than a token that only occurs in the training text.
 * Version 1 files have no validity section; all their words count as valid.
 *
 * Storage (zero copy): the model reads the file in place from the [ByteBuffer] it was loaded from, with absolute
 * little-endian gets: word bytes, folded forms, casing and validity flags, unigrams, backoff codes, context records
 * and n-gram entries are never copied. Pass a memory-mapped buffer ([load] with a [File], or a mapped asset) to keep
 * the file off the Java heap. Only small indexes that the file stores as prefix sums or varints live on the heap
 * ([heapBytes], about 4 bytes per word for each of three offset tables plus 4 bytes per 16 trigram contexts; 3.4 MB
 * for a 250k-word model): word byte offsets, bigram and trigram-context starts per word, sampled trigram entry
 * starts, a rank bit set and a hash table over the words with a folded form, the codebooks and the per-block unigram
 * maxima. [logProb] also keeps a small per-thread hash of the last context's successor lists.
 *
 * All state is immutable after loading and the buffer is only read with absolute gets, so every method is safe to
 * call from multiple threads. The model keeps the buffer (and therefore a mapping) alive as long as it lives; the
 * caller must not modify the buffer's contents.
 */
class NgramModel private constructor(
    /** Language tag from the model metadata, for example `en` or `nl`. */
    val language: String,
    /** Number of word ids including [BOS_ID]. */
    val vocabularySize: Int,
    /** Raw metadata JSON stored in the file (sources, pruning settings, statistics). */
    val metadataJson: String,
    /** Format version of the loaded file (1 or 2). */
    val formatVersion: Int,
    /** Read-only little-endian view of the file; index 0 is the file start. Only absolute gets are used. */
    private val buf: ByteBuffer,
    private val idBits: Int,
    private val entryBytes: Int,
    /** Absolute byte offset of every word in VOCB, plus the end offset at index `vocabularySize`. */
    private val vocabOffsets: IntArray,
    /** Bit `id & 63` of word `id >> 6` is set when word `id` has a FOLD entry (its folded form differs). */
    private val foldBits: LongArray,
    /** Number of FOLD entries before each 64-word block of [foldBits]. */
    private val foldRank: IntArray,
    /** Absolute byte offset of every folded form in FOLD, plus the end offset. */
    private val foldOffsets: IntArray,
    /** Open-addressing table (size a power of two, -1 = empty) of the ids of FOLD words by [hashBytes] of the word. */
    private val foldWordTable: IntArray,
    private val caseFlagsBase: Int,
    /** Word ids of the CASE "other" table (ascending) and the absolute offsets of their cased forms. */
    private val otherFormIds: IntArray,
    private val otherFormOffsets: IntArray,
    /** Absolute offsets of the VALD bit sets, or -1 for version 1 (every word valid, none listed). */
    private val validBase: Int,
    private val listedBase: Int,
    private val unigramBase: Int,
    private val unigramTop: IntArray,
    private val blockMax: FloatArray,
    private val bigramCodebook: FloatArray,
    private val trigramCodebook: FloatArray,
    private val bigramBackoffCodebook: FloatArray,
    private val trigramBackoffCodebook: FloatArray,
    /** First bigram entry of every context word, plus the entry count at index `vocabularySize`. */
    private val bigramStart: IntArray,
    private val bigramBackoffBase: Int,
    private val bigramEntriesBase: Int,
    /** First trigram context record of every `prev1` word, plus the context count at index `vocabularySize`. */
    private val trigramContextStart: IntArray,
    private val trigramRecordsBase: Int,
    /** First trigram entry of every 16th context (index `c >> TRIGRAM_SAMPLE_SHIFT`). */
    private val trigramEntrySample: IntArray,
    private val trigramEntriesBase: Int,
    /** Number of stored bigram successor entries. */
    val bigramCount: Int,
    /** Number of stored trigram contexts (prev2, prev1). */
    val trigramContextCount: Int,
    /** Number of stored trigram successor entries. */
    val trigramCount: Int,
) {
    private val idMask = (1 shl idBits) - 1
    private val entryMask = if (entryBytes >= 4) -1 else (1 shl (8 * entryBytes)) - 1
    /** Entries at or below this offset can be read with one 4-byte get; later ones are read byte by byte. */
    private val lastIntOffset = buf.limit() - 4

    /** Size of the model file in bytes (the part of the buffer this model reads). */
    val fileBytes: Int get() = buf.limit()

    /**
     * True when the model reads a direct buffer (for example a memory-mapped file or asset), so [fileBytes] are not on
     * the Java heap; false after [load] with an [InputStream], which copies the file into a heap array.
     */
    val isDirect: Boolean get() = buf.isDirect

    /**
     * Approximate Java heap bytes of this model's own index arrays (excluding object headers and the buffer). Not
     * included: the per-thread context table of [logProb], at most 8 bytes per slot of the longest hashed context
     * (128 KB for 5000 successors) on each thread that scores words.
     */
    val heapBytes: Long
        get() {
            val ints = vocabOffsets.size + foldRank.size + foldOffsets.size + foldWordTable.size + otherFormIds.size +
                otherFormOffsets.size +
                unigramTop.size + bigramStart.size + trigramContextStart.size + trigramEntrySample.size
            val floats = blockMax.size + bigramCodebook.size + trigramCodebook.size + bigramBackoffCodebook.size +
                trigramBackoffCodebook.size
            return 4L * ints + 4L * floats + 8L * foldBits.size + 2L * metadataJson.length
        }

    /**
     * Approximate bytes this model needs: [heapBytes] plus the [fileBytes] it reads. With a direct (memory-mapped)
     * buffer the file part is file-backed memory that the system pages in on use and may drop again, not Java heap.
     */
    val memoryBytes: Long get() = heapBytes + fileBytes

    // ------------------------------------------------------------------------------------------------ vocabulary

    /**
     * Id of an exact model token (lower case, straight apostrophes; see [normalizeToken]), or [UNKNOWN_ID].
     * `"<s>"` returns [BOS_ID].
     */
    fun wordId(word: String): Int {
        if (word.isEmpty()) return UNKNOWN_ID
        if (word == BOS_TOKEN) return BOS_ID
        val wordUtf8 = word.toByteArray(Charsets.UTF_8)
        val foldUtf8 = fold(word).toByteArray(Charsets.UTF_8)
        var lo = 1
        var hi = vocabularySize
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            var cmp = compareFolded(mid, foldUtf8, truncate = false)
            if (cmp == 0) cmp = compareBytes(vocabOffsets[mid], vocabOffsets[mid + 1] - vocabOffsets[mid], wordUtf8)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid
                else -> return mid
            }
        }
        // Fallback for a Unicode-version mismatch between this fold() and the exporter's fold(): the words whose folded
        // form differs from the word itself, looked up by their bytes (vocabulary words are unique).
        if (foldUtf8.size != wordUtf8.size || !foldUtf8.contentEquals(wordUtf8)) {
            val mask = foldWordTable.size - 1
            var i = hashBytes(wordUtf8) and mask
            while (true) {
                val id = foldWordTable[i]
                if (id < 0) break
                val off = vocabOffsets[id]
                if (compareBytes(off, vocabOffsets[id + 1] - off, wordUtf8) == 0) return id
                i = (i + 1) and mask
            }
        }
        return UNKNOWN_ID
    }

    /** The lower-case model token of [id] (`"<s>"` for [BOS_ID]). */
    fun word(id: Int): String {
        require(id in 0 until vocabularySize) { "word id out of range: $id" }
        return string(vocabOffsets[id], vocabOffsets[id + 1])
    }

    /**
     * True when [id] is a validated word: in the curated word list of the language, written with capitals, or frequent
     * enough in the training text to be intentional (see tools/lm/lmtool/validity.py). False for [BOS_ID] and invalid
     * ids. Every word of a version 1 file is valid.
     */
    fun isValid(id: Int): Boolean = bit(validBase, id)

    /**
     * True when [id] is a word of the curated word list (a dictionary word, as opposed to a name or a chat word that is
     * valid only through its capitals or its frequency). Every listed word is valid. False for [BOS_ID] and invalid
     * ids, and for every word of a version 1 file: without a word list nothing is known to be a dictionary word, so the
     * context-aware real-word margin ([org.florisboard.lib.prediction.EngineConfig.deltaKnownLmSlope]) stays off.
     */
    fun isListed(id: Int): Boolean = bit(listedBase, id, ifAbsent = false)

    private fun bit(base: Int, id: Int, ifAbsent: Boolean = true): Boolean {
        if (id <= 0 || id >= vocabularySize) return false
        if (base < 0) return ifAbsent
        return ((buf.get(base + (id ushr 3)).toInt() ushr (id and 7)) and 1) != 0
    }

    /** Number of validated words (excluding [BOS_ID]). */
    val validCount: Int by lazy { (1 until vocabularySize).count { isValid(it) } }

    private fun caseFlag(id: Int): Int = buf.get(caseFlagsBase + id).toInt()

    /** True when the word is mostly written with capitals mid-sentence (names like "Amsterdam", "iPhone", "FBI"). */
    fun isCapitalized(id: Int): Boolean = id in 0 until vocabularySize && caseFlag(id) != 0

    /** The dominant written form of the word, for example "Amsterdam", "iPhone", "FBI", "I'm"; else [word]. */
    fun casedForm(id: Int): String {
        val w = word(id)
        return when (caseFlag(id)) {
            CASE_TITLE -> {
                val n = Character.charCount(w.codePointAt(0))
                w.substring(0, n).uppercase(java.util.Locale.ROOT) + w.substring(n)
            }
            CASE_UPPER -> w.uppercase(java.util.Locale.ROOT)
            CASE_OTHER -> {
                val i = otherFormIds.binarySearch(id)
                if (i >= 0) string(otherFormOffsets[i], otherFormOffsets[i + 1]) else w
            }
            else -> w
        }
    }

    /**
     * Id range of the words whose folded form starts with the folded [prefix] (diacritics and apostrophes are
     * ignored, so "dont" matches "don't" and "een" matches "één"). Empty range when nothing matches. An empty prefix
     * returns all word ids (excluding [BOS_ID]).
     */
    fun prefixRange(prefix: String): IntRange {
        val p = fold(normalizeToken(prefix)).toByteArray(Charsets.UTF_8)
        var lo = 1
        var hi = vocabularySize
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compareFolded(mid, p, truncate = false) < 0) lo = mid + 1 else hi = mid
        }
        val start = lo
        hi = vocabularySize
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compareFolded(mid, p, truncate = true) <= 0) lo = mid + 1 else hi = mid
        }
        return start until lo
    }

    // ------------------------------------------------------------------------------------------------ probabilities

    /** Unigram log probability of [id]; negative infinity for [BOS_ID] or an invalid id. */
    fun unigramLogProb(id: Int): Float = if (id in 1 until vocabularySize) unigram(id) else Float.NEGATIVE_INFINITY

    /**
     * Log probability of [wordId] after the context (`prev2Id`, `prev1Id`), with backoff from trigram to bigram to
     * unigram. Negative infinity for [BOS_ID], [UNKNOWN_ID] or an invalid id.
     *
     * The engine scores many words in one context, and a frequent context has thousands of successors in the file,
     * which a lookup would scan linearly. So when the same context comes twice in a row on one thread and its lists
     * hold at least [CONTEXT_TABLE_MIN_ENTRIES] entries, they are hashed once into a per-thread table ([ContextTable])
     * that answers the following calls in constant time with exactly the same values.
     */
    fun logProb(wordId: Int, prev1Id: Int = UNKNOWN_ID, prev2Id: Int = UNKNOWN_ID): Float {
        if (wordId <= 0 || wordId >= vocabularySize) return Float.NEGATIVE_INFINITY
        val t = contextTables.get()
        if (t.prev1 != prev1Id || t.prev2 != prev2Id) {
            t.prev1 = prev1Id
            t.prev2 = prev2Id
            t.state = ContextTable.FIRST
        } else if (t.state == ContextTable.FIRST) {
            t.state = if (buildContextTable(t, prev1Id, prev2Id)) ContextTable.HASHED else ContextTable.SCAN
        }
        if (t.state != ContextTable.HASHED) return scanLogProb(wordId, prev1Id, prev2Id)
        val ids = t.ids
        val mask = t.mask
        var i = mixId(wordId) and mask
        while (true) {
            val k = ids[i]
            if (k == wordId) return t.values[i]
            if (k < 0) return t.missOffset + unigram(wordId)
            i = (i + 1) and mask
        }
    }

    /** [logProb] by scanning the context's n-gram lists (the format's lookup, see tools/lm/FORMAT.md). */
    private fun scanLogProb(wordId: Int, prev1Id: Int, prev2Id: Int): Float {
        var acc = 0f
        val ctx = trigramContext(prev1Id, prev2Id)
        if (ctx >= 0) {
            val start = trigramEntryStart(ctx)
            val code = findEntry(trigramEntriesBase, start, start + trigramLength(ctx), wordId)
            if (code >= 0) return trigramCodebook[code]
            acc += trigramBackoff(ctx)
        }
        if (prev1Id in 0 until vocabularySize) {
            val code = findEntry(bigramEntriesBase, bigramStart[prev1Id], bigramStart[prev1Id + 1], wordId)
            if (code >= 0) return acc + bigramCodebook[code]
            acc += bigramBackoff(prev1Id)
        }
        return acc + unigram(wordId)
    }

    /**
     * The [limit] most likely next words after (`prev2Id`, `prev1Id`), sorted by log probability (descending, ties by
     * id). Combines the trigram list, the bigram list scaled by the trigram backoff and the global unigram list scaled
     * by both backoffs, so the result is exact for the stored model: a word of the unigram list only gets its backoff
     * probability when neither the trigram nor the bigram list of the context holds it (a frequent word that is an
     * explicit, unlikely successor must not come back with its higher backoff score).
     */
    fun topNext(prev1Id: Int, prev2Id: Int = UNKNOWN_ID, limit: Int = 3): List<NgramCandidate> {
        if (limit <= 0) return emptyList()
        val acc = Collector(limit)
        val ctx = trigramContext(prev1Id, prev2Id)
        var bow3 = 0f
        if (ctx >= 0) {
            val start = trigramEntryStart(ctx)
            val end = start + trigramLength(ctx)
            var p = trigramEntriesBase + start * entryBytes
            for (i in start until end) {
                val e = entryAt(p)
                p += entryBytes
                acc.addIfAbsent(e and idMask, trigramCodebook[e ushr idBits])
            }
            bow3 = trigramBackoff(ctx)
        }
        var bow2 = 0f
        // Bigram ids past the first [limit] new ones: they cannot enter the top (the list is sorted by probability),
        // but they must not get a unigram-backoff score either.
        var skipped: IdSet? = null
        if (prev1Id in 0 until vocabularySize) {
            var n = 0
            var lastScore = Float.NaN
            var p = bigramEntriesBase + bigramStart[prev1Id] * entryBytes
            val start = bigramStart[prev1Id]
            val end = bigramStart[prev1Id + 1]
            for (i in start until end) {
                val e = entryAt(p)
                p += entryBytes
                val score = bow3 + bigramCodebook[e ushr idBits]
                // Entries tied with the last one taken can still win on a lower id.
                if (n < limit || score == lastScore) {
                    if (acc.addIfAbsent(e and idMask, score)) {
                        n++
                        lastScore = score
                    }
                } else {
                    val set = skipped ?: idSets.get().also { it.reset(end - i) }
                    skipped = set
                    set.add(e and idMask)
                }
            }
            bow2 = bigramBackoff(prev1Id)
        }
        val off = bow3 + bow2
        var n = 0
        for (id in unigramTop) {
            if (n >= limit) break
            if (skipped != null && skipped.contains(id)) continue
            if (acc.addIfAbsent(id, off + unigram(id))) n++
        }
        return acc.top(limit)
    }

    /**
     * The [limit] most likely words whose folded form starts with the folded [prefix], ranked by their probability
     * after (`prev2Id`, `prev1Id`) (descending, ties by id). An empty prefix is the same as [topNext].
     */
    fun topCompletions(prefix: String, prev1Id: Int, prev2Id: Int = UNKNOWN_ID, limit: Int = 3): List<NgramCandidate> {
        if (limit <= 0) return emptyList()
        val range = prefixRange(prefix)
        if (range.first == 1 && range.last == vocabularySize - 1) return topNext(prev1Id, prev2Id, limit)
        if (range.isEmpty()) return emptyList()
        val lo = range.first
        val hi = range.last + 1
        val acc = Collector(limit)
        val ctx = trigramContext(prev1Id, prev2Id)
        var bow3 = 0f
        if (ctx >= 0) {
            val start = trigramEntryStart(ctx)
            val end = start + trigramLength(ctx)
            var p = trigramEntriesBase + start * entryBytes
            for (i in start until end) {
                val e = entryAt(p)
                p += entryBytes
                val id = e and idMask
                if (id in lo until hi) acc.addIfAbsent(id, trigramCodebook[e ushr idBits])
            }
            bow3 = trigramBackoff(ctx)
        }
        var bow2 = 0f
        if (prev1Id in 0 until vocabularySize) {
            var p = bigramEntriesBase + bigramStart[prev1Id] * entryBytes
            for (i in bigramStart[prev1Id] until bigramStart[prev1Id + 1]) {
                val e = entryAt(p)
                p += entryBytes
                val id = e and idMask
                if (id in lo until hi) acc.addIfAbsent(id, bow3 + bigramCodebook[e ushr idBits])
            }
            bow2 = bigramBackoff(prev1Id)
        }
        // Best unigram-backoff words in [lo, hi) that are not already candidates: bounded top-k scan that skips
        // blocks whose maximum cannot enter the list (ids ascend, so equal scores never displace earlier ids).
        val off = bow3 + bow2
        val topIds = IntArray(limit)
        val topScores = FloatArray(limit)
        var size = 0
        var id = lo
        while (id < hi) {
            if (size == limit && (id and BLOCK_MASK) == 0 && id + BLOCK_SIZE <= hi &&
                off + blockMax[id ushr BLOCK_SHIFT] <= topScores[limit - 1]
            ) {
                id += BLOCK_SIZE
                continue
            }
            val u = unigram(id)
            if (size < limit || off + u > topScores[size - 1]) {
                if (!acc.contains(id)) {
                    val s = off + u
                    var pos = if (size < limit) size else limit - 1
                    while (pos > 0 && topScores[pos - 1] < s) {
                        topScores[pos] = topScores[pos - 1]
                        topIds[pos] = topIds[pos - 1]
                        pos--
                    }
                    topScores[pos] = s
                    topIds[pos] = id
                    if (size < limit) size++
                }
            }
            id++
        }
        for (i in 0 until size) acc.addIfAbsent(topIds[i], topScores[i])
        return acc.top(limit)
    }

    /**
     * Context ids for predicting the word that follows [textBeforeCursor] (the text up to, but not including, the
     * word being typed). Applies the training tokenisation: words are letter runs with internal apostrophes, runs that
     * contain digits are skipped, and `.`, `!`, `?` or `…` after a word start a new sentence.
     */
    fun contextOf(textBeforeCursor: CharSequence): NgramContext {
        val (w1, boundary1, end1) = previousWord(textBeforeCursor, textBeforeCursor.length)
        if (w1 == null || boundary1) return NgramContext(BOS_ID, UNKNOWN_ID)
        val prev1 = wordId(normalizeToken(w1))
        val (w2, boundary2, _) = previousWord(textBeforeCursor, end1)
        val prev2 = if (w2 == null || boundary2) BOS_ID else wordId(normalizeToken(w2))
        return NgramContext(prev1, prev2)
    }

    // ------------------------------------------------------------------------------------------------ internals

    /** The last context [logProb] saw on one thread and, once hashed, the values of its listed successors. */
    private class ContextTable {
        var prev1 = Int.MIN_VALUE
        var prev2 = Int.MIN_VALUE
        var state = FIRST
        /** Open addressing over `0..mask` (-1 = empty); [values] holds the log probability of each listed id. */
        var ids = IntArray(0)
        var values = FloatArray(0)
        var mask = 0
        /** Log probability offset of every word that neither list holds (both backoff weights). */
        var missOffset = 0f

        companion object {
            /** Seen once: scan. */
            const val FIRST = 0
            /** Seen again but the lists are short: scan. */
            const val SCAN = 1
            /** Seen again and hashed: look up. */
            const val HASHED = 2
        }
    }

    private val contextTables = object : ThreadLocal<ContextTable>() {
        override fun initialValue() = ContextTable()
    }

    /**
     * Hashes the trigram and bigram lists of the context into [t] with the values [scanLogProb] returns (trigram
     * entries first, the first occurrence of an id wins). Returns false, leaving [t] unchanged, when the lists hold
     * fewer than [CONTEXT_TABLE_MIN_ENTRIES] entries.
     */
    private fun buildContextTable(t: ContextTable, prev1Id: Int, prev2Id: Int): Boolean {
        val ctx = trigramContext(prev1Id, prev2Id)
        val triStart = if (ctx >= 0) trigramEntryStart(ctx) else 0
        val triEnd = if (ctx >= 0) triStart + trigramLength(ctx) else 0
        val hasBigrams = prev1Id in 0 until vocabularySize
        val biStart = if (hasBigrams) bigramStart[prev1Id] else 0
        val biEnd = if (hasBigrams) bigramStart[prev1Id + 1] else 0
        val n = (triEnd - triStart) + (biEnd - biStart)
        if (n < CONTEXT_TABLE_MIN_ENTRIES) return false
        val capacity = Integer.highestOneBit(2 * n - 1) shl 1
        if (t.ids.size < capacity) {
            t.ids = IntArray(capacity)
            t.values = FloatArray(capacity)
        }
        val ids = t.ids
        val values = t.values
        java.util.Arrays.fill(ids, 0, capacity, -1)
        val mask = capacity - 1
        fun put(id: Int, value: Float) {
            var i = mixId(id) and mask
            while (true) {
                val k = ids[i]
                if (k == id) return
                if (k < 0) break
                i = (i + 1) and mask
            }
            ids[i] = id
            values[i] = value
        }
        // The same float operations in the same order as scanLogProb, so the values are identical.
        var acc = 0f
        if (ctx >= 0) {
            var p = trigramEntriesBase + triStart * entryBytes
            for (i in triStart until triEnd) {
                val e = entryAt(p)
                p += entryBytes
                put(e and idMask, trigramCodebook[e ushr idBits])
            }
            acc += trigramBackoff(ctx)
        }
        if (hasBigrams) {
            var p = bigramEntriesBase + biStart * entryBytes
            for (i in biStart until biEnd) {
                val e = entryAt(p)
                p += entryBytes
                put(e and idMask, acc + bigramCodebook[e ushr idBits])
            }
            acc += bigramBackoff(prev1Id)
        }
        t.mask = mask
        t.missOffset = acc
        return true
    }

    private fun unigram(id: Int): Float {
        val q = buf.getShort(unigramBase + 2 * id).toInt() and 0xFFFF
        return if (q == UNIGRAM_BOS_Q) Float.NEGATIVE_INFINITY else -(q / UNIGRAM_SCALE)
    }

    private fun bigramBackoff(prev1Id: Int): Float =
        bigramBackoffCodebook[buf.get(bigramBackoffBase + prev1Id).toInt() and 0xFF]

    private fun trigramBackoff(ctx: Int): Float =
        trigramBackoffCodebook[buf.get(trigramRecordsBase + TRIGRAM_RECORD * ctx + 4).toInt() and 0xFF]

    private fun trigramLength(ctx: Int): Int = buf.get(trigramRecordsBase + TRIGRAM_RECORD * ctx + 3).toInt() and 0xFF

    /** First trigram entry of context [ctx]: the sampled start plus the lengths of the contexts in between. */
    private fun trigramEntryStart(ctx: Int): Int {
        var start = trigramEntrySample[ctx ushr TRIGRAM_SAMPLE_SHIFT]
        var p = trigramRecordsBase + TRIGRAM_RECORD * (ctx and TRIGRAM_SAMPLE_MASK.inv()) + 3
        val end = trigramRecordsBase + TRIGRAM_RECORD * ctx + 3
        while (p < end) {
            start += buf.get(p).toInt() and 0xFF
            p += TRIGRAM_RECORD
        }
        return start
    }

    /** The n-gram entry at absolute offset [p] (id and probability code), [entryBytes] wide. */
    private fun entryAt(p: Int): Int {
        if (p <= lastIntOffset) return buf.getInt(p) and entryMask
        var e = 0
        for (k in 0 until entryBytes) e = e or ((buf.get(p + k).toInt() and 0xFF) shl (8 * k))
        return e
    }

    /** Probability code of [wordId] among entries `from until to` of the table at [base], or -1. */
    private fun findEntry(base: Int, from: Int, to: Int, wordId: Int): Int {
        var p = base + from * entryBytes
        for (i in from until to) {
            val e = entryAt(p)
            if ((e and idMask) == wordId) return e ushr idBits
            p += entryBytes
        }
        return -1
    }

    private fun trigramContext(prev1Id: Int, prev2Id: Int): Int {
        if (prev1Id <= 0 || prev1Id >= vocabularySize || prev2Id < 0 || prev2Id >= vocabularySize) return -1
        var lo = trigramContextStart[prev1Id]
        var hi = trigramContextStart[prev1Id + 1] - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            // A 5-byte record starts with u24 prev2; the 4-byte get stays inside the record.
            val u = buf.getInt(trigramRecordsBase + TRIGRAM_RECORD * mid) and 0xFFFFFF
            when {
                u < prev2Id -> lo = mid + 1
                u > prev2Id -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    /** Index of [id] in the FOLD table, or -1 if the word folds to itself. */
    private fun foldIndex(id: Int): Int {
        val w = id ushr 6
        val bits = foldBits[w]
        val mask = 1L shl (id and 63)
        if ((bits and mask) == 0L) return -1
        return foldRank[w] + java.lang.Long.bitCount(bits and (mask - 1))
    }

    /** Compares the folded key of [id] with [p]; with [truncate] only the first p.size bytes of the key count. */
    private fun compareFolded(id: Int, p: ByteArray, truncate: Boolean): Int {
        val fi = foldIndex(id)
        val off: Int
        var len: Int
        if (fi >= 0) {
            off = foldOffsets[fi]
            len = foldOffsets[fi + 1] - off
        } else {
            off = vocabOffsets[id]
            len = vocabOffsets[id + 1] - off
        }
        if (truncate && len > p.size) len = p.size
        return compareBytes(off, len, p)
    }

    /** Compares [len] buffer bytes at [off] with [b] as unsigned bytes (shorter first on a common prefix). */
    private fun compareBytes(off: Int, len: Int, b: ByteArray): Int {
        val n = minOf(len, b.size)
        for (i in 0 until n) {
            val x = buf.get(off + i).toInt() and 0xFF
            val y = b[i].toInt() and 0xFF
            if (x != y) return x - y
        }
        return len - b.size
    }

    private fun string(from: Int, to: Int): String {
        val bytes = ByteArray(to - from)
        for (i in bytes.indices) bytes[i] = buf.get(from + i)
        return String(bytes, Charsets.UTF_8)
    }

    private val idSets = ThreadLocal.withInitial { IdSet() }

    /** A reusable open-addressing set of word ids (one per thread), cleared in O(1) by a generation stamp. */
    private class IdSet {
        private var keys = IntArray(1024)
        private var stamps = IntArray(1024)
        private var stamp = 0

        fun reset(expected: Int) {
            var cap = keys.size
            while (cap < expected * 2) cap *= 2
            if (cap != keys.size) {
                keys = IntArray(cap)
                stamps = IntArray(cap)
                stamp = 0
            }
            stamp++
            if (stamp == Int.MAX_VALUE) {
                stamps.fill(0)
                stamp = 1
            }
        }

        fun add(id: Int) {
            val mask = keys.size - 1
            var i = mix(id) and mask
            while (stamps[i] == stamp) {
                if (keys[i] == id) return
                i = (i + 1) and mask
            }
            stamps[i] = stamp
            keys[i] = id
        }

        fun contains(id: Int): Boolean {
            val mask = keys.size - 1
            var i = mix(id) and mask
            while (stamps[i] == stamp) {
                if (keys[i] == id) return true
                i = (i + 1) and mask
            }
            return false
        }

        private fun mix(id: Int): Int = (id * -0x61c88647) ushr 7
    }

    /** Candidate accumulator with an open-addressing id set, so membership checks stay O(1). */
    private class Collector(capacity: Int) {
        private var ids = IntArray(capacity * 4 + 8)
        private var scores = FloatArray(capacity * 4 + 8)
        private var size = 0
        private var table = IntArray(64) { -1 }

        fun contains(id: Int): Boolean {
            val mask = table.size - 1
            var i = mix(id) and mask
            while (true) {
                val t = table[i]
                if (t == -1) return false
                if (t == id) return true
                i = (i + 1) and mask
            }
        }

        fun addIfAbsent(id: Int, score: Float): Boolean {
            if ((size + 1) * 2 > table.size) rehash(table.size * 2)
            val mask = table.size - 1
            var i = mix(id) and mask
            while (true) {
                val t = table[i]
                if (t == id) return false
                if (t == -1) break
                i = (i + 1) and mask
            }
            table[i] = id
            if (size == ids.size) {
                ids = ids.copyOf(size * 2)
                scores = scores.copyOf(size * 2)
            }
            ids[size] = id
            scores[size] = score
            size++
            return true
        }

        private fun rehash(newSize: Int) {
            table = IntArray(newSize) { -1 }
            val mask = newSize - 1
            for (k in 0 until size) {
                var i = mix(ids[k]) and mask
                while (table[i] != -1) i = (i + 1) and mask
                table[i] = ids[k]
            }
        }

        private fun mix(id: Int): Int = (id * -0x61c88647) ushr 7

        /** Best [limit] candidates by score descending, ties by lower id (partial insertion sort, no boxing). */
        fun top(limit: Int): List<NgramCandidate> {
            val n = minOf(limit, size)
            val bestIds = IntArray(n)
            val bestScores = FloatArray(n)
            var filled = 0
            for (k in 0 until size) {
                val id = ids[k]
                val sc = scores[k]
                if (filled == n && !better(sc, id, bestScores[n - 1], bestIds[n - 1])) continue
                var pos = if (filled < n) filled++ else n - 1
                while (pos > 0 && better(sc, id, bestScores[pos - 1], bestIds[pos - 1])) {
                    bestScores[pos] = bestScores[pos - 1]
                    bestIds[pos] = bestIds[pos - 1]
                    pos--
                }
                bestScores[pos] = sc
                bestIds[pos] = id
            }
            return List(n) { NgramCandidate(bestIds[it], bestScores[it]) }
        }

        private fun better(s1: Float, id1: Int, s2: Float, id2: Int): Boolean {
            val c = s1.compareTo(s2)
            return c > 0 || (c == 0 && id1 < id2)
        }
    }

    companion object {
        const val BOS_ID = 0
        const val UNKNOWN_ID = -1
        const val BOS_TOKEN = "<s>"
        /** Newest format version this reader writes about; it also reads [FORMAT_VERSION_1]. */
        const val FORMAT_VERSION = 2
        const val FORMAT_VERSION_1 = 1

        private const val CASE_TITLE = 1
        private const val CASE_UPPER = 2
        private const val CASE_OTHER = 4
        private const val UNIGRAM_SCALE = 1024f
        private const val UNIGRAM_BOS_Q = 0xFFFF
        private const val BLOCK_SHIFT = 6
        private const val BLOCK_SIZE = 1 shl BLOCK_SHIFT
        private const val BLOCK_MASK = BLOCK_SIZE - 1
        private const val HEADER_SIZE = 32
        private const val TRIGRAM_RECORD = 5
        private const val TRIGRAM_SAMPLE_SHIFT = 4
        private const val TRIGRAM_SAMPLE_MASK = (1 shl TRIGRAM_SAMPLE_SHIFT) - 1
        /** Contexts with fewer listed successors are scanned; see [logProb]. */
        private const val CONTEXT_TABLE_MIN_ENTRIES = 32
        private val MAGIC = byteArrayOf('O'.code.toByte(), 'K'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

        /**
         * Loads a model from a stream: reads exactly the file (its size is in the header) into one heap array, which
         * the model then reads in place. The caller closes the stream. Prefer [load] with a memory-mapped buffer or a
         * [File], which keeps the file off the Java heap.
         *
         * @param maxBytes The most heap the file copy may take; a larger file throws [IllegalStateException] after the
         *  header is read, before anything is allocated.
         */
        @JvmOverloads
        fun load(input: InputStream, maxBytes: Long = Long.MAX_VALUE): NgramModel {
            val header = ByteArray(HEADER_SIZE)
            readFully(input, header, 0, HEADER_SIZE)
            require(header.copyOf(4).contentEquals(MAGIC)) { "not an Ownkey n-gram model" }
            val totalSize = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(28)
            require(totalSize >= HEADER_SIZE) { "truncated n-gram model" }
            check(totalSize <= maxBytes) { "n-gram model needs $totalSize bytes on the heap, $maxBytes allowed" }
            val bytes = ByteArray(totalSize)
            header.copyInto(bytes)
            readFully(input, bytes, HEADER_SIZE, totalSize - HEADER_SIZE)
            return load(ByteBuffer.wrap(bytes))
        }

        /** Memory-maps [file] read-only and loads the model from the mapping (zero copy; see [load] with a buffer). */
        fun load(file: File): NgramModel = RandomAccessFile(file, "r").use { raf ->
            val size = raf.length()
            require(size <= Int.MAX_VALUE) { "n-gram model too large: $size bytes" }
            // The mapping stays valid after the channel is closed.
            load(raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, size))
        }

        /**
         * Loads a model from a buffer positioned at the start of the file. The buffer's position and limit are not
         * modified; the model reads it in place (never copies it) and keeps a reference, so its contents must not
         * change afterwards. A direct buffer, such as a read-only mapping of an uncompressed asset, keeps the file
         * off the Java heap.
         */
        fun load(buffer: ByteBuffer): NgramModel {
            val whole = buffer.duplicate()
            val buf0 = whole.slice().order(ByteOrder.LITTLE_ENDIAN)
            require(buf0.limit() >= HEADER_SIZE) { "not an Ownkey n-gram model" }
            for (i in 0 until 4) require(buf0.get(i) == MAGIC[i]) { "not an Ownkey n-gram model" }
            val version = buf0.getShort(4).toInt() and 0xFFFF
            require(version == FORMAT_VERSION_1 || version == FORMAT_VERSION) { "unsupported n-gram model version $version" }
            val sectionCount = buf0.getShort(6).toInt() and 0xFFFF
            val v = buf0.getInt(8)
            val idBits = buf0.get(12).toInt() and 0xFF
            val entryBytes = buf0.get(13).toInt() and 0xFF
            val bigramCount = buf0.getInt(16)
            val trigramContextCount = buf0.getInt(20)
            val trigramCount = buf0.getInt(24)
            val totalSize = buf0.getInt(28)
            require(totalSize >= HEADER_SIZE + 12 * sectionCount && buf0.limit() >= totalSize) { "truncated n-gram model" }
            require(entryBytes in 3..4 && idBits in 1..24 && v >= 1 && (1 shl idBits) >= v) { "bad entry layout" }
            require(bigramCount >= 0 && trigramContextCount >= 0 && trigramCount >= 0) { "bad n-gram counts" }
            buf0.limit(totalSize)
            // Read-only, little-endian, index 0 = file start; queries only use absolute gets on it.
            val buf = buf0.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)

            val sections = HashMap<String, IntArray>()
            for (i in 0 until sectionCount) {
                val at = HEADER_SIZE + 12 * i
                val tag = String(ByteArray(4) { buf.get(at + it) }, Charsets.US_ASCII)
                val off = buf.getInt(at + 4)
                val len = buf.getInt(at + 8)
                require(off >= 0 && len >= 0 && off.toLong() + len <= totalSize) { "section $tag out of bounds" }
                sections[tag] = intArrayOf(off, off + len)
            }
            fun section(tag: String): IntArray = sections[tag] ?: error("missing section $tag")
            /** Fails unless `[from, to)` lies inside the section [tag] (so later absolute gets cannot fail). */
            fun requireIn(tag: String, from: Long, to: Long) {
                val (start, end) = section(tag)
                require(from >= start && to <= end && from <= to) { "truncated section $tag" }
            }

            val (metaOff, metaEnd) = section("META")
            val meta = String(ByteArray(metaEnd - metaOff) { buf.get(metaOff + it) }, Charsets.UTF_8)
            val language = Regex("\"lang\":\\s*\"([^\"]*)\"").find(meta)?.groupValues?.get(1) ?: ""

            // vocabulary: u8 len[V], then the bytes
            val (vocOff, _) = section("VOCB")
            requireIn("VOCB", vocOff.toLong(), vocOff.toLong() + v)
            val vocabOffsets = IntArray(v + 1)
            vocabOffsets[0] = vocOff + v
            for (i in 0 until v) vocabOffsets[i + 1] = vocabOffsets[i] + (buf.get(vocOff + i).toInt() and 0xFF)
            requireIn("VOCB", vocOff.toLong(), vocabOffsets[v].toLong())

            // folded forms: u32 n; u32 ids[n]; u8 len[n]; bytes
            val (foldOff, _) = section("FOLD")
            requireIn("FOLD", foldOff.toLong(), foldOff + 4L)
            val nFold = buf.getInt(foldOff)
            require(nFold in 0..v) { "bad FOLD size" }
            val foldIdsBase = foldOff + 4
            val foldLensBase = foldIdsBase + 4 * nFold
            requireIn("FOLD", foldOff.toLong(), foldLensBase.toLong() + nFold)
            val foldOffsets = IntArray(nFold + 1)
            foldOffsets[0] = foldLensBase + nFold
            for (i in 0 until nFold) foldOffsets[i + 1] = foldOffsets[i] + (buf.get(foldLensBase + i).toInt() and 0xFF)
            requireIn("FOLD", foldOff.toLong(), foldOffsets[nFold].toLong())
            val blocks64 = (v + 63) ushr 6
            val foldBits = LongArray(blocks64)
            var prevFoldId = -1
            for (i in 0 until nFold) {
                val id = buf.getInt(foldIdsBase + 4 * i)
                require(id in (prevFoldId + 1) until v) { "FOLD ids not ascending" }
                foldBits[id ushr 6] = foldBits[id ushr 6] or (1L shl (id and 63))
                prevFoldId = id
            }
            val foldRank = IntArray(blocks64)
            for (w in 1 until blocks64) foldRank[w] = foldRank[w - 1] + java.lang.Long.bitCount(foldBits[w - 1])
            val foldWordTable = IntArray(Integer.highestOneBit(maxOf(1, 2 * nFold - 1)) shl 1) { -1 }
            for (i in 0 until nFold) {
                val id = buf.getInt(foldIdsBase + 4 * i)
                val from = vocabOffsets[id]
                var h = 1
                for (k in from until vocabOffsets[id + 1]) h = 31 * h + (buf.get(k).toInt() and 0xFF)
                var slot = mixHash(h) and (foldWordTable.size - 1)
                while (foldWordTable[slot] >= 0) slot = (slot + 1) and (foldWordTable.size - 1)
                foldWordTable[slot] = id
            }

            // casing: u8 flags[V]; u32 n; u32 ids[n]; u8 len[n]; bytes
            val (caseOff, _) = section("CASE")
            requireIn("CASE", caseOff.toLong(), caseOff.toLong() + v + 4)
            val nOther = buf.getInt(caseOff + v)
            require(nOther in 0..v) { "bad CASE size" }
            val otherIdsBase = caseOff + v + 4
            val otherLensBase = otherIdsBase + 4 * nOther
            requireIn("CASE", caseOff.toLong(), otherLensBase.toLong() + nOther)
            val otherFormIds = IntArray(nOther) { buf.getInt(otherIdsBase + 4 * it) }
            val otherFormOffsets = IntArray(nOther + 1)
            otherFormOffsets[0] = otherLensBase + nOther
            for (i in 0 until nOther) otherFormOffsets[i + 1] = otherFormOffsets[i] + (buf.get(otherLensBase + i).toInt() and 0xFF)
            requireIn("CASE", caseOff.toLong(), otherFormOffsets[nOther].toLong())

            // validity (version 2): two bit sets of ceil(V / 8) bytes
            var validBase = -1
            var listedBase = -1
            if (version >= 2) {
                val (valdOff, valdEnd) = sections["VALD"] ?: error("missing section VALD")
                val n = (v + 7) / 8
                require(valdEnd - valdOff == 2 * n) { "bad VALD size" }
                validBase = valdOff
                listedBase = valdOff + n
            }

            // unigrams: u16 q[V]
            val (unigramBase, _) = section("UNIG")
            requireIn("UNIG", unigramBase.toLong(), unigramBase + 2L * v)
            val blockMax = FloatArray((v + BLOCK_SIZE - 1) / BLOCK_SIZE) { Float.NEGATIVE_INFINITY }
            for (i in 1 until v) {
                val q = buf.getShort(unigramBase + 2 * i).toInt() and 0xFFFF
                val u = if (q == UNIGRAM_BOS_Q) Float.NEGATIVE_INFINITY else -(q / UNIGRAM_SCALE)
                val b = i ushr BLOCK_SHIFT
                if (u > blockMax[b]) blockMax[b] = u
            }
            val (topOff, _) = section("UTOP")
            requireIn("UTOP", topOff.toLong(), topOff + 4L)
            val nTop = buf.getInt(topOff)
            requireIn("UTOP", topOff.toLong(), topOff + 4L + 4L * nTop)
            val unigramTop = IntArray(nTop) { buf.getInt(topOff + 4 + 4 * it) }
            require(unigramTop.all { it in 0 until v }) { "bad UTOP id" }

            // codebooks
            val qBits = 8 * entryBytes - idBits
            require(qBits in 0..12) { "bad entry layout" }
            val nq = 1 shl qBits
            val (qOff, _) = section("QTBL")
            requireIn("QTBL", qOff.toLong(), qOff + 4L * (2 * nq + 512))
            val bigramCodebook = FloatArray(nq) { buf.getFloat(qOff + 4 * it) }
            val trigramCodebook = FloatArray(nq) { buf.getFloat(qOff + 4 * (nq + it)) }
            val bow2Codebook = FloatArray(256) { buf.getFloat(qOff + 4 * (2 * nq + it)) }
            val bow3Codebook = FloatArray(256) { buf.getFloat(qOff + 4 * (2 * nq + 256 + it)) }

            // bigrams: varint len[V], u8 bowCode[V], entries
            val (bigOff, bigEnd) = section("BIGR")
            val bigramStart = IntArray(v + 1)
            var p = bigOff
            for (i in 0 until v) {
                var n = 0
                var shift = 0
                while (true) {
                    require(p < bigEnd) { "truncated section BIGR" }
                    val b = buf.get(p++).toInt() and 0xFF
                    n = n or ((b and 0x7F) shl shift)
                    shift += 7
                    if (b < 0x80) break
                    require(shift < 32) { "bad varint" }
                }
                bigramStart[i + 1] = bigramStart[i] + n
            }
            require(bigramStart[v] == bigramCount) { "bigram count mismatch" }
            val bigramBackoffBase = p
            val bigramEntriesBase = p + v
            requireIn("BIGR", bigramBackoffBase.toLong(), bigramEntriesBase + bigramCount.toLong() * entryBytes)

            // trigrams: varint count[V], NC records of 5 bytes (u24 prev2, u8 len, u8 bowCode), entries
            val (trigOff, trigEnd) = section("TRIG")
            val trigramContextStart = IntArray(v + 1)
            p = trigOff
            for (i in 0 until v) {
                var n = 0
                var shift = 0
                while (true) {
                    require(p < trigEnd) { "truncated section TRIG" }
                    val b = buf.get(p++).toInt() and 0xFF
                    n = n or ((b and 0x7F) shl shift)
                    shift += 7
                    if (b < 0x80) break
                    require(shift < 32) { "bad varint" }
                }
                trigramContextStart[i + 1] = trigramContextStart[i] + n
            }
            require(trigramContextStart[v] == trigramContextCount) { "trigram context count mismatch" }
            val trigramRecordsBase = p
            val trigramEntriesBase = p + TRIGRAM_RECORD * trigramContextCount
            requireIn("TRIG", trigramRecordsBase.toLong(), trigramEntriesBase.toLong())
            val trigramEntrySample = IntArray((trigramContextCount ushr TRIGRAM_SAMPLE_SHIFT) + 1)
            var entries = 0
            for (c in 0 until trigramContextCount) {
                if ((c and TRIGRAM_SAMPLE_MASK) == 0) trigramEntrySample[c ushr TRIGRAM_SAMPLE_SHIFT] = entries
                entries += buf.get(trigramRecordsBase + TRIGRAM_RECORD * c + 3).toInt() and 0xFF
            }
            require(entries == trigramCount) { "trigram count mismatch" }
            requireIn("TRIG", trigramEntriesBase.toLong(), trigramEntriesBase + trigramCount.toLong() * entryBytes)

            return NgramModel(
                language = language, vocabularySize = v, metadataJson = meta, formatVersion = version, buf = buf,
                idBits = idBits, entryBytes = entryBytes, vocabOffsets = vocabOffsets, foldBits = foldBits,
                foldRank = foldRank, foldOffsets = foldOffsets, foldWordTable = foldWordTable, caseFlagsBase = caseOff,
                otherFormIds = otherFormIds, otherFormOffsets = otherFormOffsets, validBase = validBase,
                listedBase = listedBase, unigramBase = unigramBase, unigramTop = unigramTop, blockMax = blockMax,
                bigramCodebook = bigramCodebook, trigramCodebook = trigramCodebook,
                bigramBackoffCodebook = bow2Codebook, trigramBackoffCodebook = bow3Codebook,
                bigramStart = bigramStart, bigramBackoffBase = bigramBackoffBase, bigramEntriesBase = bigramEntriesBase,
                trigramContextStart = trigramContextStart, trigramRecordsBase = trigramRecordsBase,
                trigramEntrySample = trigramEntrySample, trigramEntriesBase = trigramEntriesBase,
                bigramCount = bigramCount, trigramContextCount = trigramContextCount, trigramCount = trigramCount,
            )
        }

        /** Hash of UTF-8 bytes for the FOLD word table; the same function hashes the table's words at load. */
        private fun hashBytes(b: ByteArray): Int {
            var h = 1
            for (x in b) h = 31 * h + (x.toInt() and 0xFF)
            return mixHash(h)
        }

        private fun mixHash(h: Int): Int = (h * -0x61c88647) xor (h ushr 16)

        private fun mixId(id: Int): Int = (id * -0x61c88647) ushr 7

        private fun readFully(input: InputStream, into: ByteArray, offset: Int, length: Int) {
            var done = 0
            while (done < length) {
                val n = input.read(into, offset + done, length - done)
                if (n < 0) {
                    if (offset == 0 && done == 0) throw IllegalArgumentException("not an Ownkey n-gram model", EOFException())
                    throw IllegalArgumentException("truncated n-gram model", EOFException())
                }
                done += n
            }
        }

        /** Model token for a word: NFKC, curly apostrophes to `'`, lower case, edge apostrophes removed. */
        fun normalizeToken(word: String): String = NgramText.normalizeToken(word)

        /** Folded matching key: lower-case token without combining marks and apostrophes. */
        fun fold(token: String): String = NgramText.fold(token)

        private data class PrevWord(val word: String?, val boundary: Boolean, val start: Int)

        /**
         * Finds the last word token that ends at or before [end]. `boundary` is true when the gap between that word
         * and [end] holds a sentence terminator: `!`, `?` or a single `.` (an ellipsis continues the sentence).
         */
        private fun previousWord(text: CharSequence, end: Int): PrevWord {
            var i = end
            var bang = false
            var dots = 0
            var maxDots = 0
            var ellipsis = false
            while (i > 0) {
                val c = text[i - 1]
                if (NgramText.isWordChar(c)) {
                    var start = i - 1
                    while (start > 0 && NgramText.isWordChar(text[start - 1])) start--
                    val token = NgramText.wordOfRun(text.subSequence(start, i).toString())
                    if (token != null) {
                        val boundary = bang || (maxDots == 1 && !ellipsis)
                        return PrevWord(token, boundary, start)
                    }
                    i = start
                    continue
                }
                when (c) {
                    '!', '?' -> bang = true
                    '.' -> {
                        dots++
                        if (dots > maxDots) maxDots = dots
                    }
                    '…' -> ellipsis = true
                }
                if (c != '.') dots = 0
                i--
            }
            return PrevWord(null, bang || (maxDots == 1 && !ellipsis), 0)
        }
    }
}

/** Context ids for [NgramModel] queries; see [NgramModel.contextOf]. */
data class NgramContext(val prev1Id: Int, val prev2Id: Int)
