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

import android.view.KeyCharacterMap
import android.view.KeyEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import java.io.File

private const val EmojiAssetDir = "src/main/assets/ime/media/emoji"

private fun loadEmojiAsset(name: String): EmojiData {
    val moduleDir = listOf(File("."), File("app")).map { it.absoluteFile }.first { File(it, EmojiAssetDir).isDirectory }
    return File(moduleDir, "$EmojiAssetDir/$name.txt").useLines { EmojiData.parse(it) }
}

class EmojiSearchTest : FunSpec({
    val index = EmojiSearchIndex.build(listOf(loadEmojiAsset("nl"), loadEmojiAsset("en")))

    fun topResults(query: String, count: Int = 1): List<String> {
        return index.search(query).take(count).map { it.emojis.first().value }
    }

    test("Dutch words find the emoji people mean") {
        topResults("duim") shouldBe listOf("👍")
        topResults("duim omlaag") shouldBe listOf("👎")
        topResults("rood hart") shouldBe listOf("❤️")
        topResults("hart") shouldBe listOf("❤️")
    }

    test("English terms work next to Dutch ones") {
        topResults("thumbs up") shouldBe listOf("👍")
        topResults("heart") shouldBe listOf("❤️")
        topResults("cat") shouldBe listOf("🐈")
        topResults("lol", count = 3) shouldContain "😂"
    }

    test("a partly typed word already finds the emoji") {
        topResults("dui") shouldBe listOf("👍")
        topResults("thum") shouldBe listOf("👍")
        topResults("lachen", count = 3) shouldContain "😂"
    }

    test("flags are found by country name and by country code") {
        topResults("nederland") shouldBe listOf("🇳🇱")
        topResults("nl") shouldBe listOf("🇳🇱")
        topResults("be") shouldContain "🇧🇪"
    }

    test("case and accents do not matter") {
        topResults("Duim") shouldBe topResults("duim")
        topResults("CAFÉ", count = 5) shouldBe topResults("cafe", count = 5)
    }

    test("every word of a query has to match") {
        index.search("duim zebra").shouldBeEmpty()
    }

    test("blank and unknown queries find nothing") {
        index.search("   ").shouldBeEmpty()
        index.search("qqxzzv").shouldBeEmpty()
    }

    test("emojis the device cannot draw are left out") {
        val results = index.search("duim", isSupported = { it.value != "👍" }).map { it.emojis.first().value }
        results shouldNotContain "👍"
        results shouldContain "👎"
    }

    test("skin tone variants stay with their emoji instead of showing up as separate results") {
        val thumbsUp = index.search("duim omhoog").first()
        thumbsUp.emojis.first().value shouldBe "👍"
        thumbsUp.base(withSkinTone = EmojiSkinTone.MEDIUM_SKIN_TONE).value shouldBe "👍🏽"
        index.search("duim").map { it.emojis.first().value } shouldNotContain "👍🏽"
    }

    test("the keycap # emoji is read as an emoji, not as a comment line") {
        val symbols = loadEmojiAsset("root").byCategory.getValue(EmojiCategory.SYMBOLS)
        symbols.map { it.emojis.first().value } shouldContain "#️⃣"
    }

    test("every language file lists the same emojis as root") {
        val root = loadEmojiAsset("root").byCategory.mapValues { (_, sets) -> sets.map { set -> set.emojis.map { it.value } } }
        for (language in listOf("nl", "en", "de", "es", "fr", "it", "pt")) {
            val data = loadEmojiAsset(language).byCategory.mapValues { (_, sets) -> sets.map { set -> set.emojis.map { it.value } } }
            data shouldBe root
        }
    }
})

class EmojiSearchSessionTest : FunSpec({
    test("typing builds the query and closing clears it") {
        val session = EmojiSearchSession()
        session.start()
        "duim".forEach { session.type(it.toString()) }
        session.query shouldBe "duim"
        session.isActive shouldBe true
        session.stop()
        session.isActive shouldBe false
        session.query shouldBe ""
    }

    test("spaces never lead or double up") {
        val session = EmojiSearchSession()
        session.start()
        session.type(" ")
        session.query shouldBe ""
        session.type("rood")
        session.type(" ")
        session.type(" ")
        session.type("hart")
        session.query shouldBe "rood hart"
    }

    test("backspace removes one character, even one outside the BMP") {
        val session = EmojiSearchSession()
        session.start()
        session.type("ab")
        session.type("𝒳")
        session.deleteBackward()
        session.query shouldBe "ab"
        session.deleteBackward()
        session.deleteBackward()
        session.deleteBackward()
        session.query shouldBe ""
    }

    test("deleting a word keeps the words before it") {
        val session = EmojiSearchSession()
        session.start()
        session.type("rood hart")
        session.deleteWordBackward()
        session.query shouldBe "rood "
        session.deleteWordBackward()
        session.query shouldBe ""
    }

    test("editing the query forgets the best match found for the old query") {
        val session = EmojiSearchSession()
        val cat = Emoji("🐈", "cat", emptyList())
        session.start()
        session.type("cat")
        session.topResult = cat
        session.type("s")
        session.topResult shouldBe null
        session.topResult = cat
        session.deleteBackward()
        session.topResult shouldBe null
        session.topResult = cat
        session.deleteWordBackward()
        session.topResult shouldBe null
        session.type("cat")
        session.topResult = cat
        session.clear()
        session.topResult shouldBe null
    }

    test("the query stops growing at the length limit") {
        val session = EmojiSearchSession()
        session.start()
        repeat(EmojiSearchSession.MaxQueryLength + 10) { session.type("a") }
        session.query.length shouldBe EmojiSearchSession.MaxQueryLength
    }
})

class EmojiSearchHardwareKeyActionTest : FunSpec({
    fun action(keyCode: Int, unicodeChar: Int = 0, isShortcut: Boolean = false, isModifierOrSystem: Boolean = false) =
        EmojiSearchHardwareKeyAction.of(keyCode, unicodeChar, isShortcut, isModifierOrSystem)

    test("letters, space, backspace and enter edit the search and never reach the app") {
        action(KeyEvent.KEYCODE_C, unicodeChar = 'c'.code) shouldBe EmojiSearchHardwareKeyAction.TYPE
        action(KeyEvent.KEYCODE_SPACE, unicodeChar = ' '.code) shouldBe EmojiSearchHardwareKeyAction.SPACE
        action(KeyEvent.KEYCODE_DEL) shouldBe EmojiSearchHardwareKeyAction.DELETE
        action(KeyEvent.KEYCODE_ENTER, unicodeChar = '\n'.code) shouldBe EmojiSearchHardwareKeyAction.ENTER
        action(KeyEvent.KEYCODE_NUMPAD_ENTER) shouldBe EmojiSearchHardwareKeyAction.ENTER
    }

    test("forward delete and dead accent keys are swallowed instead of reaching the app") {
        val forwardDelete = action(KeyEvent.KEYCODE_FORWARD_DEL)
        forwardDelete shouldBe EmojiSearchHardwareKeyAction.IGNORE
        forwardDelete.consumesKey shouldBe true
        val deadAcute = action(KeyEvent.KEYCODE_APOSTROPHE, unicodeChar = KeyCharacterMap.COMBINING_ACCENT or 0x0301)
        deadAcute shouldBe EmojiSearchHardwareKeyAction.IGNORE
        deadAcute.consumesKey shouldBe true
    }

    test("shortcuts and navigation keys close the search before the app gets them") {
        val paste = action(KeyEvent.KEYCODE_V, unicodeChar = 'v'.code, isShortcut = true)
        paste shouldBe EmojiSearchHardwareKeyAction.CLOSE
        paste.consumesKey shouldBe false
        action(KeyEvent.KEYCODE_DPAD_LEFT) shouldBe EmojiSearchHardwareKeyAction.CLOSE
    }

    test("modifiers and system keys keep the search open and work as usual") {
        val shift = action(KeyEvent.KEYCODE_SHIFT_LEFT, isModifierOrSystem = true)
        shift shouldBe EmojiSearchHardwareKeyAction.PASS
        shift.consumesKey shouldBe false
        action(KeyEvent.KEYCODE_VOLUME_UP, isModifierOrSystem = true) shouldBe EmojiSearchHardwareKeyAction.PASS
    }
})
