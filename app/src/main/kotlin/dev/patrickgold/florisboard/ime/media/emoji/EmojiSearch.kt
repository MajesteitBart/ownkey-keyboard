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

package dev.patrickgold.florisboard.ime.media.emoji

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.github.reactivecircus.cache4k.Cache
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln

/**
 * The emoji search typed on the keyboard. While it is active, the keyboard manager adds typed characters to [query]
 * instead of sending them to the app.
 */
class EmojiSearchSession {
    var isActive by mutableStateOf(false)
        private set

    var query by mutableStateOf("")
        private set

    /**
     * The best match for [query], which the enter key inserts. The search bar sets it once the results for the
     * current query are in, and every query change clears it, so enter never inserts a match for an older query.
     */
    var topResult: Emoji? = null

    fun start() {
        query = ""
        topResult = null
        isActive = true
    }

    fun stop() {
        isActive = false
        query = ""
        topResult = null
    }

    fun type(text: String) {
        if (text.isBlank() && (query.isEmpty() || query.last().isWhitespace())) return
        if (query.length + text.length > MaxQueryLength) return
        updateQuery(query + text)
    }

    fun deleteBackward() {
        if (query.isEmpty()) return
        updateQuery(query.substring(0, query.offsetByCodePoints(query.length, -1)))
    }

    fun deleteWordBackward() {
        val trimmed = query.trimEnd()
        updateQuery(trimmed.substring(0, trimmed.lastIndexOf(' ') + 1))
    }

    fun clear() {
        updateQuery("")
    }

    private fun updateQuery(newQuery: String) {
        if (newQuery == query) return
        topResult = null
        query = newQuery
    }

    companion object {
        const val MaxQueryLength = 40
    }
}

/**
 * Finds emojis by their CLDR names and keywords, in every language the index was built from.
 */
class EmojiSearchIndex private constructor(private val entries: List<Entry>) {
    private class Entry(
        val emojiSet: EmojiSet,
        val names: List<String>,
        val keywords: List<String>,
        val words: List<String>,
        val shortestNameLength: Int,
        val popularityBonus: Int,
    )

    private class Match(val entry: Entry, val score: Int, val order: Int)

    /**
     * Returns the best matches for [query], best first. Exact names and keywords rank above prefixes, prefixes above
     * words that only appear somewhere in a name. Within that, widely used emojis and the user's [recent] ones come
     * first: CLDR's Dutch name for 🫀 is just "hart", but someone typing "hart" almost always means ❤️. Remaining ties
     * go to the emoji with the shorter name, then to palette order.
     */
    fun search(
        query: String,
        limit: Int = DefaultLimit,
        recent: Set<String> = emptySet(),
        isSupported: (Emoji) -> Boolean = { true },
    ): List<EmojiSet> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return emptyList()
        val tokens = normalizedQuery.split(' ')
        return entries.asSequence()
            .mapIndexedNotNull { order, entry ->
                score(entry, normalizedQuery, tokens)?.let { tier ->
                    val isRecent = entry.emojiSet.emojis.any { it.value in recent }
                    Match(entry, tier + if (isRecent) MaxPopularityBonus else entry.popularityBonus, order)
                }
            }
            .sortedWith(
                compareByDescending<Match> { it.score }
                    .thenBy { it.entry.shortestNameLength }
                    .thenBy { it.order },
            )
            .map { it.entry.emojiSet }
            .filter { isSupported(it.emojis.first()) }
            .take(limit)
            .toList()
    }

    private fun score(entry: Entry, query: String, tokens: List<String>): Int? {
        return when {
            entry.names.any { it == query } -> 1000
            entry.keywords.any { it == query } -> 900
            entry.names.any { it.startsWith(query) } -> 800
            entry.keywords.any { it.startsWith(query) } -> 700
            tokens.all { token -> entry.words.any { it.startsWith(token) } } -> 600
            query.length >= 3 && (entry.names.any { query in it } || entry.keywords.any { query in it }) -> 300
            else -> null
        }
    }

    companion object {
        const val DefaultLimit = 80

        /**
         * At most this much is added for popularity, on tiers 100 apart. The bonus falls with the logarithm of the
         * rank, as use roughly halves each time the rank doubles. So only the handful of most used emojis, like ❤️,
         * can pass a less used one from the next tier up, and no emoji jumps two tiers.
         */
        private const val MaxPopularityBonus = 150

        /**
         * The 300 most used emojis, most used first, from the Unicode Consortium's 2019 ranking by median frequency of
         * use (https://home.unicode.org/emoji/emoji-frequency/). Skin tone and gender variants count as one emoji there.
         */
        private val PopularEmojis: Map<String, Int> = """
            😂 ❤️ 😍 🤣 😊 🙏 💕 😭 😘 👍 😅 👏 😁 ♥️ 🔥 💔 💖 💙 😢 🤔 😆 🙄 💪 😉 ☺️ 👌 🤗 💜 😔 😎
            😇 🌹 🤦 🎉 ‼️ 💞 ✌️ ✨ 🤷 😱 😌 🌸 🙌 😋 💗 💚 😏 💛 🙂 💓 🤩 😄 😀 🖤 😃 💯 🙈 👇 🎶 😒
            🤭 ❣️ ❗ 😜 💋 👀 😪 😑 💥 🙋 😞 😩 😡 🤪 👊 ☀️ 😥 🤤 👉 💃 😳 ✋ 😚 😝 😴 🌟 😬 🙃 🍀 🌷
            😻 😓 ⭐ ✅ 🌈 😈 🤘 💦 ✔️ 😣 🏃 💐 ☹️ 🎊 💘 😠 ☝️ 😕 🌺 🎂 🌻 😐 🖕 💝 🙊 😹 🗣️ 💫 💀 👑
            🎵 🤞 😛 🔴 😤 🌼 😫 ⚽ 🤙 ☕ 🏆 🧡 🎁 ⚡ 🌞 🎈 ❌ ✊ 👋 😲 🌿 🤫 👈 😮 🙆 🍻 🍃 🐶 💁 😰
            🤨 😶 🤝 🚶 💰 🍓 💢 🇺🇸 🤟 🙁 🚨 💨 🤬 ✈️ 🎀 🍺 🤓 😙 💟 🌱 😖 👶 ▶️ ➡️ ❓ 💎 💸 ⬇️ 😨 🌚
            🦋 😷 🕺 ⚠️ 🙅 😟 😵 👎 🤲 🤠 🤧 📌 🔵 💅 🧐 🐾 🍒 😗 🤑 🚀 🌊 🤯 🐷 ☎️ 💧 😯 💆 👆 🎤 🙇
            🍑 ❄️ 🌴 🇧🇷 💣 🐸 💌 📍 🥀 🤢 👅 💡 💩 ⁉️ 👐 📸 👻 🤐 🤮 🎼 ✍️ 🚩 🍎 🍊 👼 💍 📣 🥂 ⤵️ 📱
            ☔ 🌙 🍾 🎧 🍁 ⭕ 🏀 ☠️ ⚫ 🖐️ 😧 🎯 📲 ☘️ 👁️ 🍷 👄 🐟 🍰 💤 🕊️ 📺 💭 🐱 🐝 🇲🇽 🧚 🔝 📢 📷
            🐕 🎸 🔫 🤚 🍭 🍆 💉 🌎 😦 🌀 👿 ☑️ 🎥 🌧️ 👽 🍋 🤒 🤡 🍫 📚 🏁 🤕 🦄 🍅 🚗 🚫 💵 ⚾ 🔪 🔔
        """.split(' ', '\n').filter { it.isNotBlank() }.withIndex()
            .associate { (rank, emoji) -> withoutVariationSelector(emoji) to rank }

        private fun withoutVariationSelector(emoji: String) = emoji.replace("\uFE0F", "")

        private fun popularityBonus(emojiSet: EmojiSet): Int {
            val rank = PopularEmojis[withoutVariationSelector(emojiSet.emojis.first().value)] ?: return 0
            val share = 1.0 - ln(rank + 1.0) / ln(PopularEmojis.size + 1.0)
            return (MaxPopularityBonus * share).toInt()
        }

        private val CombiningMarks = """\p{Mn}+""".toRegex()
        private val Whitespace = """\s+""".toRegex()
        private val WordSeparators = """[\s:,.()"!?/’'-]+""".toRegex()

        private val cache = Cache.Builder<List<FlorisLocale>, EmojiSearchIndex>()
            .maximumCacheSize(2)
            .build()

        /** Lowercases [text], removes accents and collapses whitespace, so "Één  Hart" and "een hart" match. */
        fun normalize(text: String): String {
            val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            return decomposed.replace(CombiningMarks, "").replace(Whitespace, " ").trim()
        }

        /**
         * Builds an index for [locales]. English is always included, because many emoji terms ("lol", "ok", "+1")
         * are English whatever language someone types in.
         */
        suspend fun forLocales(context: Context, locales: List<FlorisLocale>): EmojiSearchIndex {
            val languages = (locales + FlorisLocale.ENGLISH).distinctBy { it.language }
            return cache.get(languages) {
                build(languages.map { EmojiData.get(context, it) })
            }
        }

        private const val RegionalIndicatorA = 0x1F1E6
        private const val RegionalIndicatorZ = 0x1F1FF

        /** Returns "nl" for 🇳🇱, so country flags are also found by their code. */
        private fun regionCode(emoji: String): String? {
            val codePoints = emoji.codePoints().toArray()
            if (codePoints.size != 2 || codePoints.any { it !in RegionalIndicatorA..RegionalIndicatorZ }) return null
            return codePoints.joinToString("") { ('a' + (it - RegionalIndicatorA)).toString() }
        }

        fun build(sources: List<EmojiData>): EmojiSearchIndex {
            class Builder(val emojiSet: EmojiSet) {
                val names = linkedSetOf<String>()
                val keywords = linkedSetOf<String>()
            }

            val builders = LinkedHashMap<String, Builder>()
            for (source in sources) {
                for (emojiSets in source.byCategory.values) {
                    for (emojiSet in emojiSets) {
                        val base = emojiSet.emojis.first()
                        val builder = builders.getOrPut(base.value) { Builder(emojiSet) }
                        normalize(base.name).takeIf { it.isNotEmpty() }?.let { builder.names += it }
                        base.keywords.forEach { keyword ->
                            normalize(keyword).takeIf { it.isNotEmpty() }?.let { builder.keywords += it }
                        }
                        regionCode(base.value)?.let { builder.keywords += it }
                    }
                }
            }
            val entries = builders.values
                .filter { it.names.isNotEmpty() || it.keywords.isNotEmpty() }
                .map { builder ->
                    val words = (builder.names + builder.keywords)
                        .flatMap { it.split(WordSeparators) }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    Entry(
                        emojiSet = builder.emojiSet,
                        names = builder.names.toList(),
                        keywords = builder.keywords.toList(),
                        words = words,
                        shortestNameLength = builder.names.minOfOrNull { it.length } ?: Int.MAX_VALUE,
                        popularityBonus = popularityBonus(builder.emojiSet),
                    )
                }
            return EmojiSearchIndex(entries)
        }
    }
}
