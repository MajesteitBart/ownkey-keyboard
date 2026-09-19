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

package dev.patrickgold.florisboard.ime.text.rewrite

import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import dev.patrickgold.jetpref.datastore.runtime.LoadStrategy
import dev.patrickgold.jetpref.datastore.runtime.PersistStrategy
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

class RewritePromptPersistenceTest : FunSpec({
    val samples = listOf(
        "single line" to "First sentence. Second sentence.",
        "paragraph breaks" to "First sentence.\n\nSecond sentence.",
        "Windows line endings" to "First sentence.\r\nSecond sentence.",
        "carriage returns" to "First sentence.\rSecond sentence.",
        "quoted words" to "Use \"quoted\" words.",
        "literal escapes" to "Keep \\n and \\r and \\u000a literally.",
        "backslash before quote" to "Keep \\\" and \\\\ literally.",
        "tabs" to "First\tSecond",
        "unicode and semicolons" to "Caf\u00e9; \uD83D\uDE00",
    )

    samples.forEach { (label, instruction) ->
        test("edited and added voices survive repeated saves and fresh datastore loads with $label") {
            withPreferenceFile { storage, path ->
                val saved = listOf(
                    RewritePromptPreset("improve", "Edited built-in", instruction),
                    RewritePromptPreset("custom_test", "Added voice", instruction),
                )
                val encoded = RewritePromptPresets.encode(saved)
                // The storage representation is still ordinary JSON, readable by older builds.
                Json.decodeFromString<List<RewritePromptPreset>>(encoded) shouldContainExactly saved

                val initial = jetprefDataStoreOf(FlorisPreferenceModel::class)
                val initialPrefs by initial
                initial.init(LoadStrategy.Disabled, PersistStrategy.UseWriter(storage)).getOrThrow()
                initialPrefs.voxtral.rewritePrompts.set(encoded).getOrThrow()

                repeat(3) { restart ->
                    val restarted = jetprefDataStoreOf(FlorisPreferenceModel::class)
                    val prefs by restarted
                    // Also prove new saves survive JetPref's unmodified reader (older app builds).
                    val reader = if (restart == 0) storage else RewritePromptPreferenceReader(storage)
                    restarted.init(LoadStrategy.UseReader(reader), PersistStrategy.UseWriter(storage)).getOrThrow()
                    prefs.voxtral.rewritePrompts.get() shouldBe encoded
                    RewritePromptPresets.decode(prefs.voxtral.rewritePrompts.get()) shouldContainExactly saved
                    prepareForJetPrefRewrite(path)
                    prefs.voxtral.rewritePrompts.set(RewritePromptPresets.encode(saved)).getOrThrow()
                }
            }
        }
    }

    test("saving the current default voices survives a fresh datastore load") {
        withPreferenceFile { storage, _ ->
            val initial = jetprefDataStoreOf(FlorisPreferenceModel::class)
            val initialPrefs by initial
            initial.init(LoadStrategy.Disabled, PersistStrategy.UseWriter(storage)).getOrThrow()
            initialPrefs.voxtral.rewritePrompts.set(RewritePromptPresets.defaultJson).getOrThrow()

            val restarted = jetprefDataStoreOf(FlorisPreferenceModel::class)
            val prefs by restarted
            restarted.init(LoadStrategy.UseReader(storage), PersistStrategy.Disabled).getOrThrow()
            prefs.voxtral.rewritePrompts.get() shouldBe RewritePromptPresets.defaultJson
        }
    }

    test("startup recovers legacy multiline voices before JetPref decoding without writing the original file") {
        withPreferenceFile { storage, path ->
            val saved = samples.mapIndexed { index, (_, instruction) ->
                RewritePromptPreset("custom_$index", "Saved voice $index", instruction)
            }
            val original = legacyPreferenceFile(saved)
            path.toFile().writeText(original)
            val reader = RewritePromptPreferenceReader(storage)
            val readOnce = reader.read()
            val adaptedPath = path.resolveSibling("adapted.jetpref")
            try {
                adaptedPath.toFile().writeText(readOnce)
                // Repeated adaptation does not change escaping or other preferences.
                RewritePromptPreferenceReader(FileBasedStorage(adaptedPath.toString())).read() shouldBe readOnce
            } finally {
                Files.deleteIfExists(adaptedPath)
            }

            val restarted = jetprefDataStoreOf(FlorisPreferenceModel::class)
            val prefs by restarted
            restarted.init(LoadStrategy.UseReader(reader), PersistStrategy.UseWriter(storage)).getOrThrow()
            RewritePromptPresets.decode(prefs.voxtral.rewritePrompts.get()) shouldContainExactly saved
            prefs.voxtral.postProcessingModel.get() shouldBe "saved-model"
            path.toFile().readText() shouldBe original

            // Any later preference write must preserve the recovered voices, even on another load.
            prepareForJetPrefRewrite(path)
            prefs.voxtral.postProcessingModel.set("changed-model").getOrThrow()
            val next = jetprefDataStoreOf(FlorisPreferenceModel::class)
            val nextPrefs by next
            next.init(LoadStrategy.UseReader(storage), PersistStrategy.Disabled).getOrThrow()
            RewritePromptPresets.decode(nextPrefs.voxtral.rewritePrompts.get()) shouldContainExactly saved
        }
    }

    listOf(ImportStrategy.Merge, ImportStrategy.Erase).forEach { strategy ->
        test("backup restore recovers legacy edited voices and preserves them on export with $strategy") {
            withPreferenceFile { storage, path ->
                val saved = listOf("clean", "formal", "business").map {
                    RewritePromptPreset(it, "Edited $it", "First line.\nSecond line; \\n literally.")
                }
                path.toFile().writeText(legacyPreferenceFile(saved))
                val restored = jetprefDataStoreOf(FlorisPreferenceModel::class)
                val prefs by restored
                restored.init(LoadStrategy.Disabled, PersistStrategy.Disabled).getOrThrow()
                restored.import(strategy, RewritePromptPreferenceReader(storage)).getOrThrow()
                RewritePromptPresets.decode(prefs.voxtral.rewritePrompts.get()) shouldContainExactly saved
                prepareForJetPrefRewrite(path)
                restored.export(storage).getOrThrow()

                val next = jetprefDataStoreOf(FlorisPreferenceModel::class)
                val nextPrefs by next
                next.init(LoadStrategy.UseReader(storage), PersistStrategy.Disabled).getOrThrow()
                RewritePromptPresets.decode(nextPrefs.voxtral.rewritePrompts.get()) shouldContainExactly saved
            }
        }
    }

    test("escape combinations retain exact content in both existing files and new saves") {
        val chars = listOf('\\', 'n', 'r', '"', '\n', '\r', '\t', '\u00e9')
        val saved = chars.flatMap { first ->
            chars.map { second ->
                RewritePromptPreset("pair_${first.code}_${second.code}", "Escape pair", "Text $first$second end")
            }
        }
        withPreferenceFile { storage, path ->
            path.toFile().writeText(legacyPreferenceFile(saved))
            val initial = jetprefDataStoreOf(FlorisPreferenceModel::class)
            val prefs by initial
            initial.init(
                LoadStrategy.UseReader(RewritePromptPreferenceReader(storage)),
                PersistStrategy.UseWriter(storage),
            ).getOrThrow()
            RewritePromptPresets.decode(prefs.voxtral.rewritePrompts.get()) shouldContainExactly saved
            prepareForJetPrefRewrite(path)
            prefs.voxtral.rewritePrompts.set(RewritePromptPresets.encode(saved)).getOrThrow()
            val next = jetprefDataStoreOf(FlorisPreferenceModel::class)
            val nextPrefs by next
            next.init(LoadStrategy.UseReader(storage), PersistStrategy.Disabled).getOrThrow()
            RewritePromptPresets.decode(nextPrefs.voxtral.rewritePrompts.get()) shouldContainExactly saved
        }
    }

    test("the recovery reader preserves malformed entries and unrelated preferences byte for byte") {
        withPreferenceFile { storage, path ->
            val original = "s;voxtral__post_processing_model;\"saved-model\"\r\n" +
                "s;voxtral__rewrite_prompts;\"unterminated\r\n" +
                "s;other_setting;\"literal \\\\n\"\r\n"
            path.toFile().writeText(original)
            RewritePromptPreferenceReader(storage).read() shouldBe original
            path.toFile().readText() shouldBe original
        }
    }
})

/** The legacy writer stored ordinary JSON inside JetPref's quoted string entry. */
private fun legacyPreferenceFile(saved: List<RewritePromptPreset>): String {
    val legacyJson = Json.encodeToString(saved)
    return "s;voxtral__post_processing_model;\"saved-model\"\r\n" +
        "s;voxtral__rewrite_prompts;${Json.encodeToString(legacyJson)}\r\n"
}

private fun prepareForJetPrefRewrite(path: Path) {
    // JetPref uses File.renameTo, which replaces an existing destination on Android/Linux but not
    // Windows. Remove only the test fixture on Windows so the real JetPref writer can run there too.
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        Files.deleteIfExists(path)
    }
}

private suspend fun withPreferenceFile(block: suspend (FileBasedStorage, Path) -> Unit) {
    val directory = Files.createTempDirectory("ownkey-voice-persistence-")
    val path = directory.resolve("preferences.jetpref")
    try {
        block(FileBasedStorage(path.toString()), path)
    } finally {
        Files.deleteIfExists(path.resolveSibling("preferences.jetpref.tmp"))
        Files.deleteIfExists(path)
        Files.deleteIfExists(directory)
    }
}
