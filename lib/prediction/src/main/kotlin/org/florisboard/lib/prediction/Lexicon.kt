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

package org.florisboard.lib.prediction

/**
 * The words of one [WordLanguageModel] grouped by key form ([TextFolding.keyForm]) and sorted lexicographically.
 *
 * A *position* `0 until size` addresses one distinct key form; its *group* lists the model ids that share it,
 * best unigram first (`"een"` -> `[een, één, eén]`, `"dont"` -> `[don't, dont]`). The sorted key forms act as a
 * virtual trie: the words below a prefix form a contiguous position range, and children are found by binary
 * search, so no node objects exist. A blocked sparse table over the best unigram per position answers top-k
 * queries for any range in `O(k (log k + B))`.
 *
 * Everything is stored in primitive arrays, about 30 bytes per word ([heapBytes]; 7.6 MB for 250k Dutch words): the
 * key-form characters one byte each ([KeyChars]), five int or float tables and the range-maximum table. No word
 * strings are kept; [keyForm] builds one on demand. [build] sorts the whole vocabulary: for 150k words it took about 70 ms warm and 80-160 ms
 * cold on a desktop JVM. That has not been measured on ART, where it is likely several times slower, so never call
 * [build] (or construct a [PredictionEngine]) on the main thread. Instances are immutable and thread-safe.
 */
class Lexicon private constructor(
    /** The model whose ids the groups contain. */
    val model: WordLanguageModel,
    private val chars: KeyChars,
    private val keyStart: IntArray,
    private val groupStart: IntArray,
    private val groupIds: IntArray,
    private val best: FloatArray,
    private val posOfId: IntArray,
) {
    /** Number of distinct key forms. */
    val size: Int get() = best.size

    /** Number of model ids in all groups. */
    val wordCount: Int get() = groupIds.size

    private val rmq = RangeArgMax(best)

    /**
     * Approximate Java heap bytes of this lexicon's arrays (excluding object headers and the model): the key-form
     * characters, the position, group and id tables and the range-maximum table. No [String] is kept.
     */
    val heapBytes: Long
        get() = chars.heapBytes + 4L * (keyStart.size + groupStart.size + groupIds.size + best.size + posOfId.size) +
            rmq.heapBytes

    fun keyLength(pos: Int): Int = keyStart[pos + 1] - keyStart[pos]

    fun keyForm(pos: Int): String = chars.string(keyStart[pos], keyLength(pos))

    /** Character [depth] of the key form at [pos]; [depth] must be below [keyLength]. */
    fun charAt(pos: Int, depth: Int): Char = chars[keyStart[pos] + depth]

    fun groupSize(pos: Int): Int = groupStart[pos + 1] - groupStart[pos]

    /** Model id number [index] of the group at [pos]; index 0 has the best unigram. */
    fun groupId(pos: Int, index: Int): Int = groupIds[groupStart[pos] + index]

    /** Best unigram log probability within the group at [pos]. */
    fun bestUnigram(pos: Int): Double = best[pos].toDouble()

    /** Sorted position of model id [id], or -1 if the word has an empty key form or is the sentence token. */
    fun positionOf(id: Int): Int = if (id >= 0 && id < posOfId.size) posOfId[id] else -1

    /** Position of [keyForm], or -1. */
    fun find(keyForm: CharSequence): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            val c = compareKey(mid, keyForm)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid
                else -> return mid
            }
        }
        return -1
    }

    /** Positions whose key form starts with [prefix], as `lo until hi` (empty when there are none). */
    fun prefixRange(prefix: CharSequence): IntRange {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (comparePrefix(mid, prefix) < 0) lo = mid + 1 else hi = mid
        }
        val start = lo
        hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (comparePrefix(mid, prefix) <= 0) lo = mid + 1 else hi = mid
        }
        return start until lo
    }

    /**
     * First position in `[lo, hi)` whose character at [depth] is greater than [c]. All key forms in the range must
     * be longer than [depth] and share their first [depth] characters.
     */
    fun upperBoundChar(lo: Int, hi: Int, depth: Int, c: Char): Int {
        var l = lo
        var h = hi
        while (l < h) {
            val mid = (l + h) ushr 1
            if (chars[keyStart[mid] + depth] <= c) l = mid + 1 else h = mid
        }
        return l
    }

    /**
     * Writes up to [k] positions of `[lo, hi)` with the best unigram into [out], best first (ties by position),
     * and returns how many were written.
     */
    fun topByUnigram(lo: Int, hi: Int, k: Int, out: IntArray): Int {
        if (lo >= hi || k <= 0) return 0
        val limit = minOf(k, out.size, hi - lo)
        val cap = 2 * limit + 2
        val hLo = IntArray(cap)
        val hHi = IntArray(cap)
        val hArg = IntArray(cap)
        var size = 0

        fun better(a: Int, b: Int): Boolean = best[a] > best[b] || (best[a] == best[b] && a < b)

        fun push(l: Int, h: Int) {
            val arg = rmq.argMax(l, h)
            var i = size++
            while (i > 0) {
                val parent = (i - 1) shr 1
                if (!better(arg, hArg[parent])) break
                hLo[i] = hLo[parent]; hHi[i] = hHi[parent]; hArg[i] = hArg[parent]
                i = parent
            }
            hLo[i] = l; hHi[i] = h; hArg[i] = arg
        }

        push(lo, hi)
        var count = 0
        while (count < limit && size > 0) {
            val l = hLo[0]
            val h = hHi[0]
            val m = hArg[0]
            size--
            if (size > 0) {
                val lastLo = hLo[size]
                val lastHi = hHi[size]
                val lastArg = hArg[size]
                var i = 0
                while (true) {
                    val left = 2 * i + 1
                    if (left >= size) break
                    val right = left + 1
                    val child = if (right < size && better(hArg[right], hArg[left])) right else left
                    if (!better(hArg[child], lastArg)) break
                    hLo[i] = hLo[child]; hHi[i] = hHi[child]; hArg[i] = hArg[child]
                    i = child
                }
                hLo[i] = lastLo; hHi[i] = lastHi; hArg[i] = lastArg
            }
            out[count++] = m
            if (l < m) push(l, m)
            if (m + 1 < h) push(m + 1, h)
        }
        return count
    }

    private fun compareKey(pos: Int, key: CharSequence): Int {
        val start = keyStart[pos]
        val len = keyStart[pos + 1] - start
        val n = minOf(len, key.length)
        for (i in 0 until n) {
            val a = chars[start + i]
            val b = key[i]
            if (a != b) return a.compareTo(b)
        }
        return len - key.length
    }

    /** Negative if the key at [pos] sorts before every key starting with [prefix], 0 if it starts with it. */
    private fun comparePrefix(pos: Int, prefix: CharSequence): Int {
        val start = keyStart[pos]
        val len = keyStart[pos + 1] - start
        val n = minOf(len, prefix.length)
        for (i in 0 until n) {
            val a = chars[start + i]
            val b = prefix[i]
            if (a != b) return a.compareTo(b)
        }
        return if (len >= prefix.length) 0 else -1
    }

    companion object {
        /** Groups and sorts all words of [model]; see the class documentation. */
        fun build(model: WordLanguageModel): Lexicon {
            val v = model.vocabularySize
            val kfs = arrayOfNulls<String>(v)
            val uni = FloatArray(v)
            val ids = IntArray(v)
            var m = 0
            for (id in 0 until v) {
                if (id == model.bosId) continue
                val kf = TextFolding.keyForm(model.word(id))
                if (kf.isEmpty()) continue
                kfs[id] = kf
                uni[id] = model.unigramLogProb(id).toFloat()
                ids[m++] = id
            }
            val byKey = Comparator<Int> { a, b -> kfs[a]!!.compareTo(kfs[b]!!) }
            val full = Comparator<Int> { a, b ->
                val c = kfs[a]!!.compareTo(kfs[b]!!)
                when {
                    c != 0 -> c
                    uni[a] != uni[b] -> uni[b].compareTo(uni[a])
                    else -> a.compareTo(b)
                }
            }
            var keySorted = true
            for (i in 1 until m) {
                if (byKey.compare(ids[i - 1], ids[i]) > 0) {
                    keySorted = false
                    break
                }
            }
            val order: IntArray
            if (keySorted) {
                // Already in key order (for example a vocabulary sorted by folded form): only order each group.
                order = ids.copyOf(m)
                var i = 0
                while (i < m) {
                    var j = i + 1
                    while (j < m && kfs[order[j]] == kfs[order[i]]) j++
                    if (j - i > 1) {
                        val g = order.copyOfRange(i, j).toTypedArray()
                        java.util.Arrays.sort(g, full)
                        for (t in g.indices) order[i + t] = g[t]
                    }
                    i = j
                }
            } else {
                val boxed = ids.copyOf(m).toTypedArray()
                java.util.Arrays.sort(boxed, full)
                order = boxed.toIntArray()
            }

            var n = 0
            var totalChars = 0
            for (i in 0 until m) {
                if (i == 0 || kfs[order[i]] != kfs[order[i - 1]]) {
                    n++
                    totalChars += kfs[order[i]]!!.length
                }
            }
            val chars = KeyChars.Builder(totalChars)
            val keyStart = IntArray(n + 1)
            val groupStart = IntArray(n + 1)
            val best = FloatArray(n)
            val posOfId = IntArray(v) { -1 }
            var p = -1
            var c = 0
            for (i in 0 until m) {
                val id = order[i]
                val kf = kfs[id]!!
                if (i == 0 || kf != kfs[order[i - 1]]) {
                    p++
                    keyStart[p] = c
                    chars.append(kf)
                    c += kf.length
                    groupStart[p] = i
                    best[p] = uni[id]
                }
                posOfId[id] = p
            }
            keyStart[n] = c
            groupStart[n] = m
            return Lexicon(model, chars.build(), keyStart, groupStart, order, best, posOfId)
        }
    }
}

/**
 * The concatenated key forms of a [Lexicon], one byte per character: key forms are lower case and folded, so nearly
 * every character is in U+0001..U+00FF (half the size of a [CharArray]). Any other character is stored as a 0 byte
 * plus an entry in a sorted exception list ("œ", "ł", "ı" and the like; a handful per vocabulary).
 */
internal class KeyChars private constructor(
    private val bytes: ByteArray,
    private val exceptionIndex: IntArray,
    private val exceptionChars: CharArray,
) {
    val size: Int get() = bytes.size

    val heapBytes: Long get() = bytes.size + 4L * exceptionIndex.size + 2L * exceptionChars.size

    operator fun get(i: Int): Char {
        val b = bytes[i].toInt() and 0xFF
        return if (b != 0) b.toChar() else exceptionChars[exceptionIndex.binarySearch(i)]
    }

    fun string(start: Int, length: Int): String = String(CharArray(length) { get(start + it) })

    class Builder(capacity: Int) {
        private val bytes = ByteArray(capacity)
        private var size = 0
        private var exceptionIndex = IntArray(8)
        private var exceptionChars = CharArray(8)
        private var exceptions = 0

        fun append(s: String) {
            for (ch in s) {
                if (ch.code in 1..0xFF) {
                    bytes[size] = ch.code.toByte()
                } else {
                    if (exceptions == exceptionIndex.size) {
                        exceptionIndex = exceptionIndex.copyOf(2 * exceptions)
                        exceptionChars = exceptionChars.copyOf(2 * exceptions)
                    }
                    exceptionIndex[exceptions] = size
                    exceptionChars[exceptions] = ch
                    exceptions++
                }
                size++
            }
        }

        fun build(): KeyChars {
            check(size == bytes.size) { "expected ${bytes.size} characters, got $size" }
            return KeyChars(bytes, exceptionIndex.copyOf(exceptions), exceptionChars.copyOf(exceptions))
        }
    }
}

/**
 * Range arg-max over a float array: a sparse table over blocks of [BLOCK] values plus linear scans inside the two
 * boundary blocks. Memory is `n / BLOCK * log2(n / BLOCK)` ints instead of `n log n` for a plain sparse table.
 * Ties resolve to the lower index.
 */
internal class RangeArgMax(private val values: FloatArray) {
    private val blocks = (values.size + BLOCK - 1) / BLOCK
    private val table: Array<IntArray>

    init {
        val levels = if (blocks == 0) 0 else 32 - Integer.numberOfLeadingZeros(blocks)
        table = Array(levels) { IntArray(blocks - (1 shl it) + 1) }
        if (levels > 0) {
            val base = table[0]
            for (b in 0 until blocks) base[b] = scan(b * BLOCK, minOf(values.size, (b + 1) * BLOCK))
            for (j in 1 until levels) {
                val prev = table[j - 1]
                val cur = table[j]
                val half = 1 shl (j - 1)
                for (b in cur.indices) cur[b] = pick(prev[b], prev[b + half])
            }
        }
    }

    /** Approximate heap bytes of the block table (the values belong to the caller). */
    val heapBytes: Long get() = table.sumOf { 4L * it.size }

    /** Index of the maximum in `[lo, hi)`, which must be non-empty. */
    fun argMax(lo: Int, hi: Int): Int {
        val bl = lo / BLOCK
        val bh = (hi - 1) / BLOCK
        if (bh - bl <= 1) return scan(lo, hi)
        var best = pick(scan(lo, (bl + 1) * BLOCK), scan(bh * BLOCK, hi))
        val first = bl + 1
        val count = bh - first
        val j = 31 - Integer.numberOfLeadingZeros(count)
        best = pick(best, table[j][first])
        best = pick(best, table[j][bh - (1 shl j)])
        return best
    }

    private fun scan(lo: Int, hi: Int): Int {
        var best = lo
        for (i in lo + 1 until hi) {
            if (values[i] > values[best]) best = i
        }
        return best
    }

    private fun pick(a: Int, b: Int): Int {
        return if (values[b] > values[a] || (values[b] == values[a] && b < a)) b else a
    }

    companion object {
        const val BLOCK = 16
    }
}
