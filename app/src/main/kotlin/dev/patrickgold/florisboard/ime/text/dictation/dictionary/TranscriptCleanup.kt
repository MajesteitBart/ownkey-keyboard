/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation.dictionary

import java.util.regex.Matcher
import java.util.regex.Pattern

/** One saved spelling correction: a case-insensitive whole-word source and its literal replacement. */
data class CorrectionRule(val source: String, val replacement: String)

/** Everything the cleaner needs, resolved once per snapshot so no rule is compiled on the input path. */
data class CleanupSettings(
    val removeFillers: Boolean,
    val fillerWords: List<String>,
    val corrections: List<CorrectionRule>,
)

/** Shared by filler matching, sentence repair, and the neutral filler-only result. */
internal object TranscriptPunctuation {
    // These marks also delimit unspaced sentences. Keep Latin dots conservative for names/domains.
    const val UNSPACED_END = "。！？؟۔।॥։܀܁܂።⸮"
    const val SENTENCE_END = ".!?…‥" + UNSPACED_END
    const val FOLLOWING_MARKS = SENTENCE_END + ",;:、，：；؛"
    const val MARKS = FOLLOWING_MARKS + "'\"¡¿·"
    val followingPattern: String = "[${Pattern.quote(FOLLOWING_MARKS)}]"
    val unspacedEndPattern: String = "[${Pattern.quote(UNSPACED_END)}]"
}

/**
 * Deterministic transcript cleanup without a language model, ported from the Windows reference.
 *
 * Fillers are removed first, then corrections run in saved order and may cascade (`A → B` followed by
 * `B → C` yields `C`). Neither step understands meaning: the rules only protect word boundaries,
 * apostrophes, hyphens, line breaks and intentional casing such as `iPhone`.
 */
object TranscriptCleanup {
    private const val SENTENCE_END = TranscriptPunctuation.SENTENCE_END
    // Built from its code point on purpose: a literal NUL in the source makes git treat this file
    // as binary, which hides diffs, blame and text-based review.
    private val MARK: Char = Char(0)

    // Explicit Unicode classes instead of `\w` and `\s`: the JVM needs UNICODE_CHARACTER_CLASS for
    // those to be Unicode aware, and Android's ICU regex rejects that flag outright.
    private const val WORD_CHAR = "[\\p{L}\\p{M}\\p{N}_]"
    private const val WORD_EDGE = "[\\p{L}\\p{M}\\p{N}_'’-]"
    private const val SPACE = "[\\s\\p{Zs}\\u2028\\u2029]"
    private const val NON_SPACE = "[^\\s\\p{Zs}\\u2028\\u2029]"
    private const val FLAGS = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE

    private val whitespace = Regex("$SPACE+")
    private val markedWord = Pattern.compile("$MARK$SPACE*($NON_SPACE+)")
    private val spaceBeforePunctuation = Pattern.compile("[ \\t]+(${TranscriptPunctuation.followingPattern})")
    private val repeatedSpaces = Pattern.compile("[ \\t]{2,}")
    private val spacedLineBreak = Pattern.compile(" *\\n *")

    /** Collapses inner whitespace and trims, so `Orukeet\tv1` and ` Orukeet v1 ` are the same term. */
    fun normalizeTerm(value: String): String =
        value.trim().split(whitespace).filter { it.isNotEmpty() }.joinToString(" ")

    /** Unique, non-empty terms in their original order. Duplicates compare case-insensitively. */
    fun normalizeVocabulary(values: Iterable<String>): List<String> {
        val seen = HashSet<String>()
        val terms = ArrayList<String>()
        for (value in values) {
            val term = normalizeTerm(value)
            if (term.isNotEmpty() && seen.add(term.lowercase())) terms.add(term)
        }
        return terms
    }

    /** Rules with both sides filled, distinct case-insensitive sources, and no identical pairs. */
    fun normalizeCorrections(rules: Iterable<CorrectionRule>): List<CorrectionRule> {
        val seen = HashSet<String>()
        val result = ArrayList<CorrectionRule>()
        for (rule in rules) {
            val source = normalizeTerm(rule.source)
            val replacement = normalizeTerm(rule.replacement)
            if (source.isEmpty() || replacement.isEmpty() || source == replacement) continue
            if (!seen.add(source.lowercase())) continue
            result.add(CorrectionRule(source, replacement))
        }
        return result
    }

    fun compileFillerPattern(words: List<String>): Pattern? {
        val usable = words.filter { it.isNotEmpty() }
        if (usable.isEmpty()) return null
        val alternatives = usable.sortedByDescending { it.length }.joinToString("|") { Pattern.quote(it) }
        val punctuation = TranscriptPunctuation.followingPattern
        val sentenceEnd = TranscriptPunctuation.unspacedEndPattern
        // Retain closing quotes; an apostrophe inside a word is not a closing boundary.
        val closingQuote = "[\"'”’»](?=$SPACE|$punctuation|$)"
        val post = "(?:$punctuation*$sentenceEnd$punctuation*|$punctuation*(?=$SPACE|$|$closingQuote))"
        return Pattern.compile(
            "(?<pre>,)?(?<lead>^|$SPACE+|(?<=$sentenceEnd))(?<!$WORD_EDGE)" +
                "(?<word>(?:$alternatives)(?:$SPACE+(?:$alternatives))*)(?!$WORD_EDGE)(?<post>$post)",
            FLAGS,
        )
    }

    fun compileCorrection(rule: CorrectionRule): Pattern =
        Pattern.compile("(?<!$WORD_CHAR)${Pattern.quote(rule.source)}(?!$WORD_CHAR)", FLAGS)

    /** Drops hesitations and repairs the surrounding punctuation and spacing. */
    fun removeFillers(text: String, words: List<String>): String {
        if (text.isEmpty()) return text
        val pattern = compileFillerPattern(words) ?: return text
        return removeFillers(text, pattern)
    }

    internal fun removeFillers(text: String, pattern: Pattern): String {
        if (text.isEmpty()) return text
        var current = text
        var previous: String? = null
        while (previous != current) {
            previous = current
            current = substituteFillers(current, pattern)
        }
        current = replaceAll(markedWord, current) { match -> capitalize(match.group(1)!!) }
        current = current.replace(MARK.toString(), "")
        current = spaceBeforePunctuation.matcher(current).replaceAll("$1")
        current = repeatedSpaces.matcher(current).replaceAll(" ")
        current = spacedLineBreak.matcher(current).replaceAll("\n")
        return current.trim()
    }

    private fun substituteFillers(text: String, pattern: Pattern): String {
        val matcher = pattern.matcher(text)
        val output = StringBuilder(text.length)
        var last = 0
        while (matcher.find()) {
            output.append(text, last, matcher.start())
            output.append(fillerReplacement(text, matcher))
            last = matcher.end()
        }
        output.append(text, last, text.length)
        return output.toString()
    }

    private fun fillerReplacement(text: String, match: Matcher): String {
        val before = text.substring(0, match.start()).trimEnd()
        val pre = match.group("pre")
        val lead = match.group("lead").orEmpty()
        val post = match.group("post").orEmpty()
        val lineStart = '\n' in lead
        val beforeClosingMarks = before.trimEnd { it in "\"'”’»)]}" }
        var sentenceStart = lineStart || before.isEmpty() ||
            beforeClosingMarks.lastOrNull()?.let { it in SENTENCE_END } == true || before.lastOrNull() == MARK
        val ending = post.firstOrNull { it in SENTENCE_END }?.toString().orEmpty()
        if (pre != null && !lineStart) sentenceStart = false
        return when {
            // Keep the line break; the word that now opens the line gets its capital.
            lineStart -> pre.orEmpty() + lead + MARK
            // Mark the spot so the word that now opens the sentence gets its capital.
            sentenceStart -> (if (before.isNotEmpty()) lead else "") + MARK
            ending.isNotEmpty() -> ending
            pre != null -> ","
            else -> ""
        }
    }

    /** Capitalizes a plain lowercase word; intentional casing such as `iPhone` or `eBay` is left alone. */
    internal fun capitalize(word: String): String {
        val start = word.indexOfFirst { it !in "\"'“‘«¿¡([{" }
        if (start < 0) return word
        val first = word[start]
        if (first.isLowerCase() && word.substring(start + 1).none { it.isUpperCase() }) {
            return word.substring(0, start) + word.substring(start, start + 1).uppercase() + word.substring(start + 1)
        }
        return word
    }

    /** Replaces whole-word misspellings with their preferred spelling, literally and in saved order. */
    fun applyCorrections(text: String, rules: List<CorrectionRule>): String {
        var current = text
        for (rule in normalizeCorrections(rules)) {
            current = compileCorrection(rule).matcher(current).replaceAll(Matcher.quoteReplacement(rule.replacement))
        }
        return current
    }

    private inline fun replaceAll(pattern: Pattern, text: String, replacement: (Matcher) -> String): String {
        val matcher = pattern.matcher(text)
        val output = StringBuilder(text.length)
        var last = 0
        while (matcher.find()) {
            output.append(text, last, matcher.start())
            output.append(replacement(matcher))
            last = matcher.end()
        }
        output.append(text, last, text.length)
        return output.toString()
    }
}

/**
 * Compiled cleanup for one dictionary snapshot. Construct it off the UI and input threads; [clean]
 * itself only runs the compiled rules.
 */
class TranscriptCleaner(val settings: CleanupSettings) {
    private val fillerPattern: Pattern? =
        if (settings.removeFillers) TranscriptCleanup.compileFillerPattern(settings.fillerWords) else null
    private val corrections: List<Pair<Pattern, String>> =
        TranscriptCleanup.normalizeCorrections(settings.corrections).map { rule ->
            TranscriptCleanup.compileCorrection(rule) to Matcher.quoteReplacement(rule.replacement)
        }

    val isIdentity: Boolean get() = fillerPattern == null && corrections.isEmpty()

    /** The text after filler removal and after the corrections that followed it. */
    data class Cleaned(val afterFillers: String, val text: String)

    fun clean(text: String): String = cleanDetailed(text).text

    fun cleanDetailed(text: String): Cleaned {
        if (text.isEmpty()) return Cleaned(text, text)
        val afterFillers = fillerPattern?.let { pattern -> TranscriptCleanup.removeFillers(text, pattern) } ?: text
        var current = afterFillers
        for ((pattern, replacement) in corrections) {
            current = pattern.matcher(current).replaceAll(replacement)
        }
        return Cleaned(afterFillers, current)
    }
}
