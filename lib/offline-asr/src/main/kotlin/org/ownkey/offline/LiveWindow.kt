package org.ownkey.offline

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A recognized word and when it starts, in seconds from the start of the recording. */
data class TimedWord(val text: String, val start: Double)

/** Words that became final since the last update, and the words that may still change. */
data class LiveUpdate(val frozen: List<String>, val live: List<String>)

/**
 * Decides which part of a live dictation is still decoded again. Only the end of the transcript stays
 * live: the sentence being spoken, and never more than [Config.maxLiveWords] words. Everything before
 * it freezes and is never decoded again, so each preview covers a few seconds of audio however long the
 * dictation runs. When nothing new is said for a moment, the live words settle and become final too:
 * decoding the same speech again with more and more silence after it only makes the result drift.
 * Plain logic without audio or native code, so the rules are testable.
 *
 * Feed it each preview's words with [accept] and the last decode's words with [finish]. Decode the
 * audio from [decodeStart] to the end of the recording.
 */
class LiveWindow(private val config: Config = Config()) {
    data class Config(
        /** Live words before the oldest ones freeze without a sentence end. */
        val maxLiveWords: Int = 12,
        /** Words left live after freezing at [maxLiveWords]. */
        val keepWordsAfterCap: Int = 8,
        /** Speech that must follow a sentence end before it counts. The model ends every preview with a full stop. */
        val confirmAfterSeconds: Double = 1.5,
        /** Audio decoded before the first live word, so the seam keeps its casing and no words repeat. */
        val contextSeconds: Double = 2.0,
        /** A live part spanning more audio than this freezes, which bounds the cost of quiet stretches. */
        val maxLiveSeconds: Double = 20.0,
        /** Silence kept before the next words after a quiet stretch closes. */
        val quietLeadSeconds: Double = 2.0,
        /** No new word for this long after the last one started, and two previews agree: the live words settle. */
        val settleAfterSeconds: Double = 2.0,
        /** Settle even when the last two previews disagree; past this the decoder only drifts. */
        val settleAnywayAfterSeconds: Double = 3.5,
        /** With nothing live, quiet audio beyond this is left out of the next decodes. */
        val quietWindowSeconds: Double = 4.0,
    ) {
        init {
            require(maxLiveWords >= 1 && keepWordsAfterCap in 1..maxLiveWords) { "keepWordsAfterCap must be within 1..maxLiveWords" }
        }
    }

    /** A sentence mark held back when the live words settled in a pause. The speech after it, or Stop, decides. */
    private class HeldMark(val mark: String, val start: Double, val word: String)

    private val frozenStarts = ArrayList<Double>()
    private var lastFrozen: String? = null
    private var live: List<TimedWord> = emptyList()
    private var previous: List<TimedWord> = emptyList()

    /** Words that start before this time are frozen. */
    private var keepFrom = 0.0

    /** Audio end of the decode that produced [live]. */
    private var coveredTo = 0.0

    private var held: HeldMark? = null

    /** The first word after a pause that turned out not to end a sentence; later readings may capitalize it. */
    private var seamWord: TimedWord? = null

    /** Nothing is live: everything said so far is final. */
    val isSettled: Boolean get() = live.isEmpty()

    /**
     * The last word of a settle, and the end of the audio it excluded. A word the settling preview hadn't
     * written out yet can still turn up in that audio; see [withSettledGap].
     */
    private var settledLast: TimedWord? = null
    private var settledGapEnd = 0.0

    /** Previews in a row that heard nothing after [keepFrom], and where the one before the newest ended. */
    private var quietPreviews = 0
    private var previousQuietEnd = 0.0

    /**
     * Where the next decode starts. It snaps to the start of a frozen word, because a decode that
     * begins in the middle of a word sometimes returns nothing at all.
     */
    fun decodeStart(): Double {
        var target = keepFrom - config.contextSeconds
        // After a short pause the decode reaches back to the word with the held mark, so the decoder reads it
        // with the new speech after it. A decode that starts in the silence can't tell, and doesn't always
        // capitalize a new sentence either.
        held?.let { if (keepFrom - it.start <= REACH_BACK_SECONDS) target = min(target, it.start - 1.0) }
        // With nothing live, the cut sits in the silence after the last words, and two seconds back reaches only
        // the last one or two. Orukeet reads a few words followed by silence badly and invents words after them
        // ("en minzaam" became "And Mint John."), so the decode starts a few words earlier while they're close.
        if (live.isEmpty()) {
            frozenStarts.getOrNull(frozenStarts.size - CONTEXT_WORDS)
                ?.takeIf { keepFrom - it <= REACH_BACK_SECONDS }
                ?.let { target = min(target, it) }
        }
        if (target <= 0.0) return 0.0
        val word = frozenStarts.lastOrNull { it <= target }
        // No frozen word just before the target means it falls in silence, which is a safe place to start.
        if (word == null || target - word > 1.0) return target
        return max(0.0, word - 0.1)
    }

    /** One preview. [words] may start before the cut; those are dropped. Returns null when nothing changed. */
    fun accept(words: List<TimedWord>, audioEnd: Double): LiveUpdate? {
        val frozen = ArrayList<String>()
        val heard = withSettledGap(words)
        if (heard.isNotEmpty()) releaseHeldMark(words, heard.first(), frozen)
        val hypothesis = seamCase(heard)
        if (hypothesis.isNotEmpty()) {
            quietPreviews = 0
            val unchanged = sameWords(hypothesis, previous)
            live = hypothesis
            coveredTo = audioEnd
            val cut = freezePoint(hypothesis, audioEnd)
            if (cut > 0) freeze(cut, frozen)
            val quiet = live.lastOrNull()?.let { audioEnd - it.start } ?: 0.0
            if (quiet >= config.settleAfterSeconds && (unchanged || quiet >= config.settleAnywayAfterSeconds)) settle(frozen)
            previous = hypothesis
        } else {
            // An empty preview is ignored: Orukeet sometimes returns nothing for audio with speech in it.
            quietPreviews++
        }
        // Nothing live and still quiet: leave out audio that two previews in a row found empty. The newest
        // preview alone doesn't count, because a word that has only just started isn't written out yet.
        if (live.isEmpty() && quietPreviews >= 2 && audioEnd - keepFrom > config.quietWindowSeconds) {
            keepFrom = max(keepFrom, previousQuietEnd - config.quietLeadSeconds)
        }
        if (hypothesis.isEmpty()) previousQuietEnd = audioEnd
        if (audioEnd - keepFrom > config.maxLiveSeconds) closeQuietStretch(audioEnd, heard = hypothesis.isNotEmpty(), frozen)
        if (hypothesis.isEmpty() && frozen.isEmpty()) return null
        return LiveUpdate(frozen, displayed(live))
    }

    /**
     * The audio since the last decode is quiet, so it wasn't decoded: nothing new was said. The live words
     * settle once the last of them is old enough, and a long quiet stretch leaves the window.
     */
    fun quiet(audioEnd: Double): LiveUpdate? {
        val frozen = ArrayList<String>()
        val last = live.lastOrNull()
        if (last != null && audioEnd - last.start >= config.settleAfterSeconds) settle(frozen)
        if (live.isEmpty() && audioEnd - keepFrom > config.quietWindowSeconds) {
            keepFrom = max(keepFrom, audioEnd - config.quietLeadSeconds)
        }
        if (audioEnd - keepFrom > config.maxLiveSeconds) closeQuietStretch(audioEnd, heard = false, frozen)
        return if (frozen.isEmpty()) null else LiveUpdate(frozen, displayed(live))
    }

    /** The last decode, after Stop. Every remaining word becomes final, with its own punctuation. */
    fun finish(words: List<TimedWord>): LiveUpdate {
        val frozen = ArrayList<String>()
        val heard = withSettledGap(words)
        if (heard.isNotEmpty()) {
            releaseHeldMark(words, heard.first(), frozen)
        } else {
            held?.let { frozen += it.mark }
            held = null
        }
        frozen += seamCase(heard).ifEmpty { live }.map { it.text }
        live = emptyList()
        return LiveUpdate(frozen, emptyList())
    }

    /**
     * Nothing new for a while: the live words become final now, while the decoder still reads them well.
     * A sentence mark at the end is held back, because the model puts one at every pause, also in the
     * middle of a sentence; the speech after the pause decides it, or Stop does.
     */
    private fun settle(frozen: MutableList<String>) {
        val last = live.last()
        val mark = last.text.takeLastWhile { it in HELD_MARKS }
        val bare = last.text.dropLast(mark.length)
        if (mark.isNotEmpty() && bare.isNotEmpty()) {
            live = live.dropLast(1) + last.copy(text = bare)
            held = HeldMark(mark, last.start, normalized(bare))
        }
        freeze(live.size, frozen)
        // Speech that started in the last half second of this decode isn't written out yet, so it stays in.
        keepFrom = max(last.start + SETTLED_WORD_SECONDS, coveredTo - JUST_STARTED_SECONDS)
        settledLast = if (mark.isNotEmpty() && bare.isNotEmpty()) last.copy(text = bare) else last
        settledGapEnd = keepFrom
    }

    /**
     * The words of a decode from [keepFrom] on, plus the words between the last settled word and the audio
     * the settle excluded. The decoder sometimes writes out a word only seconds after it was said ("en
     * minzaam" settling as "en"), so a new word there still counts. A later reading that only splits the
     * settled word differently ("bonte" as "bon te") adds nothing.
     */
    private fun withSettledGap(words: List<TimedWord>): List<TimedWord> {
        val heard = words.filter { it.start >= keepFrom }
        val last = settledLast ?: return heard
        val before = words.filter { it.start < keepFrom }
        // Where the decode read the settled word again, else the word starting right where it did, which is that
        // word read differently. With neither, the decode started after it. Start times alone can't tell, because
        // the decoder may time two short words 0.16 s apart ("en minzaam").
        val reread = rereadOf(before, last)
            ?: before.indices.filter { abs(before[it].start - last.start) <= SAME_WORD_SECONDS }
                .minByOrNull { abs(before[it].start - last.start) }?.let { it..it }
        val gap = (if (reread != null) before.drop(reread.last + 1) else before.filter { it.start > last.start })
            .filter { it.start < settledGapEnd }
        if (gap.isEmpty()) return heard
        // From now on these are ordinary live words: the cut moves back between them and the settled word.
        keepFrom = max(last.start, (last.start + gap.first().start) / 2)
        settledLast = null
        return gap + heard
    }

    /**
     * Speech after a settled pause. When the decode still covers the word before the pause, its own
     * punctuation there decides; otherwise a capital at the start of the new speech marks a new sentence.
     */
    private fun releaseHeldMark(words: List<TimedWord>, firstNew: TimedWord, frozen: MutableList<String>) {
        val mark = held ?: return
        held = null
        // Only words before the new speech: a piece of it must not lend the settled word its punctuation.
        val before = words.subList(0, words.indexOf(firstNew).coerceAtLeast(0))
        val again = rereadOf(before, TimedWord(mark.word, mark.start))?.let { before[it.last] }
        val decided = when {
            again != null -> again.text.takeLastWhile { it in HELD_MARKS }
            firstNew.text.first().isUpperCase() -> mark.mark
            else -> ""
        }
        if (decided.isEmpty()) {
            if (firstNew.text.first().isLowerCase()) seamWord = firstNew
            return
        }
        frozen += decided
        lastFrozen = (lastFrozen ?: "") + decided
    }

    /**
     * Where [words] read the [settled] word again: the same word, or the same letters split differently ("bonte"
     * as "bon te"), starting closest to where it did. The indices of its pieces, or null.
     */
    private fun rereadOf(words: List<TimedWord>, settled: TimedWord): IntRange? {
        val target = normalized(settled.text)
        val near = words.indices.filter { abs(words[it].start - settled.start) <= REREAD_SECONDS }
        for (first in near.sortedBy { abs(words[it].start - settled.start) }) {
            var joined = normalized(words[first].text)
            if (joined.isEmpty() || !target.startsWith(joined)) continue
            var last = first
            while (joined.length < target.length && last + 1 < words.size && target.startsWith(joined + normalized(words[last + 1].text))) {
                last++
                joined += normalized(words[last].text)
            }
            if (joined == target) return first..last
        }
        return null
    }

    /** Whether two previews read the same words for the live part, ignoring case and punctuation. */
    private fun sameWords(words: List<TimedWord>, before: List<TimedWord>): Boolean {
        val from = words.first().start - SAME_WORD_SECONDS
        val other = before.filter { it.start >= from }
        return words.size == other.size && words.indices.all { normalized(words[it].text) == normalized(other[it].text) }
    }

    private fun freezePoint(words: List<TimedWord>, audioEnd: Double): Int {
        var cut = 0
        for (i in 0 until words.size - 1) {
            val word = words[i]
            if (endsSentence(word.text) && audioEnd - words[i + 1].start >= config.confirmAfterSeconds && wasSentenceEnd(word)) {
                cut = i + 1
            }
        }
        if (words.size - cut > config.maxLiveWords) cut = words.size - config.keepWordsAfterCap
        return cut
    }

    /** The full stop flips between previews, so it must have been there, on the same word, last time too. */
    private fun wasSentenceEnd(word: TimedWord): Boolean = previous.any {
        endsSentence(it.text) && abs(it.start - word.start) <= SAME_WORD_SECONDS && normalized(it.text) == normalized(word.text)
    }

    private fun freeze(count: Int, into: MutableList<String>) {
        settledLast = null
        seamWord = null
        val words = live.take(count)
        into += words.map { it.text }
        words.forEach { frozenStarts += it.start }
        lastFrozen = words.last().text
        keepFrom = if (count < live.size) (words.last().start + live[count].start) / 2 else coveredTo
        live = live.drop(count)
    }

    /**
     * The live part spans more audio than [Config.maxLiveSeconds]. Every branch moves the window forward,
     * so a long backlog or a run of empty decodes can't make the same range decode again and again.
     */
    private fun closeQuietStretch(audioEnd: Double, heard: Boolean, frozen: MutableList<String>) {
        when {
            live.isEmpty() -> keepFrom = max(keepFrom, audioEnd - config.quietLeadSeconds)
            // The newest decode heard nothing after the last words: keep them and move past the quiet audio.
            !heard -> {
                freeze(live.size, frozen)
                keepFrom = max(keepFrom, audioEnd - config.quietLeadSeconds)
            }
            coveredTo - live.last().start >= QUIET_AFTER_WORD_SECONDS -> {
                freeze(live.size, frozen)
                keepFrom = coveredTo - config.quietLeadSeconds
            }
            live.size >= 2 -> freeze(live.size - 1, frozen)
            else -> keepFrom = live.first().start - 0.1
        }
    }

    /**
     * After a frozen sentence end the next word opens a sentence, whatever the context decode thought. A word
     * that was decided to continue the sentence across a pause stays lowercase when a later reading flips it.
     */
    private fun seamCase(words: List<TimedWord>): List<TimedWord> {
        val first = words.firstOrNull() ?: return words
        val seam = seamWord
        if (seam != null && abs(first.start - seam.start) <= SAME_WORD_SECONDS && normalized(first.text) == normalized(seam.text)) {
            if (!first.text.first().isUpperCase()) return words
            return listOf(first.copy(text = first.text.replaceFirstChar { it.lowercase() })) + words.drop(1)
        }
        val previousWord = lastFrozen ?: return words
        if (!endsSentence(previousWord) || !first.text.first().isLowerCase()) return words
        return listOf(first.copy(text = first.text.replaceFirstChar { it.titlecase() })) + words.drop(1)
    }

    /** Every preview ends in a full stop because the audio stops there; it isn't shown until it's real. */
    private fun displayed(words: List<TimedWord>): List<String> {
        val texts = words.map { it.text }
        val last = texts.lastOrNull() ?: return texts
        if (!last.endsWith('.') || last.endsWith("...")) return texts
        val trimmed = last.dropLast(1)
        return if (trimmed.isEmpty()) texts.dropLast(1) else texts.dropLast(1) + trimmed
    }

    companion object {
        private const val SAME_WORD_SECONDS = 0.16

        /** A settled word read again with the same letters may have moved this far. */
        private const val REREAD_SECONDS = 0.4
        private const val QUIET_AFTER_WORD_SECONDS = 3.0
        private const val HELD_MARKS = ".,!?…;:"

        /** Past the start of the last settled word: its later readings start within a frame of it. */
        private const val SETTLED_WORD_SECONDS = 0.2

        /** Speech that started this close to the end of a decode may not be written out yet. */
        private const val JUST_STARTED_SECONDS = 0.5

        /** How far back a decode may reach for the word with a held mark, or for words of context. */
        private const val REACH_BACK_SECONDS = 6.0

        /** Frozen words a decode starts with when nothing is live. */
        private const val CONTEXT_WORDS = 6
        private val sentenceEnd = Regex("[.!?…]['\")\\]”’»]*$")

        fun endsSentence(word: String): Boolean = sentenceEnd.containsMatchIn(word)

        private fun normalized(word: String): String = word.lowercase().filter { it.isLetterOrDigit() }

        /** Joins decoder tokens into words. A token that starts with a space or `▁` starts a new word. */
        fun words(tokens: Array<String>, timestamps: FloatArray, durationSeconds: Double): List<TimedWord> {
            if (tokens.isEmpty()) return emptyList()
            val times = if (timestamps.size == tokens.size) timestamps.map { it.toDouble() }
                else tokens.indices.map { durationSeconds * it / tokens.size }
            val words = ArrayList<TimedWord>()
            val current = StringBuilder()
            var start = 0.0
            tokens.forEachIndexed { i, token ->
                val startsWord = token.startsWith(' ') || token.startsWith('▁')
                if (startsWord && current.isNotBlank()) {
                    words += TimedWord(current.toString().trim(), start)
                    current.setLength(0)
                }
                if (current.isEmpty()) start = times[i]
                current.append(token.trimStart(' ', '▁'))
            }
            if (current.isNotBlank()) words += TimedWord(current.toString().trim(), start)
            return words
        }
    }
}
