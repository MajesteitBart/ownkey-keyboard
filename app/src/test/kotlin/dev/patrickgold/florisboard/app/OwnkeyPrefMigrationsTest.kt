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

package dev.patrickgold.florisboard.app

import dev.patrickgold.florisboard.ime.smartbar.SmartbarLayout
import dev.patrickgold.florisboard.ime.theme.ThemeIconStyle
import dev.patrickgold.florisboard.ime.theme.ThemeMode
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import dev.patrickgold.jetpref.datastore.runtime.LoadStrategy
import dev.patrickgold.jetpref.datastore.runtime.PersistStrategy
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class OwnkeyPrefMigrationsTest : FunSpec({
    test("only the extended toolbar layout survives a load") {
        SmartbarLayout.entries.forEach { layout ->
            val expected = if (layout == SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED) {
                MigrationDecision.Keep
            } else {
                MigrationDecision.Reset
            }
            OwnkeyPrefMigrations.smartbarLayout(layout.name) shouldBe expected
        }
        OwnkeyPrefMigrations.smartbarLayout("NOT_A_LAYOUT") shouldBe MigrationDecision.Reset
    }

    test("follow time becomes follow system and every other theme mode stays") {
        OwnkeyPrefMigrations.themeMode(ThemeMode.FOLLOW_TIME.name) shouldBe
            MigrationDecision.Transform(ThemeMode.FOLLOW_SYSTEM.name)
        ThemeMode.entries.filter { it != ThemeMode.FOLLOW_TIME }.forEach { mode ->
            OwnkeyPrefMigrations.themeMode(mode.name) shouldBe MigrationDecision.Keep
        }
    }

    test("hidden settings reset whatever they hold") {
        val stored = mapOf(
            "ai__local_vocabulary_hints" to listOf("true", "false"),
            "other__show_app_icon" to listOf("false", "true"),
            "theme__toolbar_icon_color" to listOf("ff00ff0000000000", "10"),
            "theme__toolbar_icon_size_percent" to listOf("80", "100"),
            "theme__icon_style" to listOf("FILLED", "HEROICONS_MINI"),
        )
        stored.forEach { (key, values) ->
            values.forEach { raw -> OwnkeyPrefMigrations.decide(key, raw) shouldBe MigrationDecision.Reset }
        }
    }

    test("decide routes each locked key to its rule") {
        OwnkeyPrefMigrations.decide("smartbar__layout", SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED.name) shouldBe
            MigrationDecision.Reset
        OwnkeyPrefMigrations.decide("smartbar__layout", SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED.name) shouldBe
            MigrationDecision.Keep
        OwnkeyPrefMigrations.decide("theme__mode", ThemeMode.FOLLOW_TIME.name) shouldBe
            MigrationDecision.Transform(ThemeMode.FOLLOW_SYSTEM.name)
        OwnkeyPrefMigrations.decide("theme__mode", ThemeMode.ALWAYS_DAY.name) shouldBe MigrationDecision.Keep
    }

    test("the locks cover exactly the listed keys and leave the others to the existing rules") {
        OwnkeyPrefMigrations.keys shouldBe setOf(
            "smartbar__layout",
            "ai__local_vocabulary_hints",
            "other__show_app_icon",
            "theme__toolbar_icon_color",
            "theme__toolbar_icon_size_percent",
            "theme__icon_style",
            "theme__mode",
        )
        listOf(
            "smartbar__action_arrangement",
            "advanced__show_app_icon",
            "smartbar__extended_actions_expanded",
            "suggestion__display_mode",
            "theme__glass_night_theme_id",
            "theme__accent_color",
        ).forEach { key -> OwnkeyPrefMigrations.decide(key, "anything") shouldBe null }
    }

    test("a migrated value is stable on the next load") {
        OwnkeyPrefMigrations.smartbarLayout(SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED.name) shouldBe
            MigrationDecision.Keep
        val migrated = OwnkeyPrefMigrations.themeMode(ThemeMode.FOLLOW_TIME.name) as MigrationDecision.Transform
        OwnkeyPrefMigrations.themeMode(migrated.rawValue) shouldBe MigrationDecision.Keep
    }

    test("a renamed entry gets the lock of its new key") {
        OwnkeyPrefMigrations.decideRenamed("other__show_app_icon", "false") shouldBe MigrationDecision.Reset
        OwnkeyPrefMigrations.decideRenamed("theme__mode", ThemeMode.FOLLOW_TIME.name) shouldBe
            MigrationDecision.Transform(ThemeMode.FOLLOW_SYSTEM.name)
        OwnkeyPrefMigrations.decideRenamed("other__settings_language", "nl") shouldBe MigrationDecision.Keep
    }

    // jetpref runs migrate() once per stored entry and stores a renamed entry without migrating it again, so these
    // load real files through the datastore instead of calling the decisions directly.
    context("locks hold through a real load and restore") {
        val lockedFile = listOf(
            "b;advanced__show_app_icon;false",
            "s;smartbar__layout;\"${SmartbarLayout.SUGGESTIONS_ONLY.name}\"",
            "s;theme__mode;\"${ThemeMode.FOLLOW_TIME.name}\"",
            "b;ai__local_vocabulary_hints;true",
            "s;theme__icon_style;\"${ThemeIconStyle.FILLED.name}\"",
            "i;theme__toolbar_icon_size_percent;80",
            "s;other__settings_language;\"nl\"",
        ).joinToString("\r\n", postfix = "\r\n")

        fun FlorisPreferenceModel.shouldHoldLockedValues() {
            other.showAppIcon.get() shouldBe true
            smartbar.layout.get() shouldBe SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED
            theme.mode.get() shouldBe ThemeMode.FOLLOW_SYSTEM
            voxtral.localVocabularyHints.get() shouldBe false
            theme.iconStyle.get() shouldBe ThemeIconStyle.HEROICONS_MINI
            theme.toolbarIconSizePercent.get() shouldBe 100
            // Renames without a lock still carry their value.
            other.settingsLanguage.get() shouldBe "nl"
        }

        test("on load, including the legacy app icon key") {
            withPreferenceFile(lockedFile) { storage ->
                val store = jetprefDataStoreOf(FlorisPreferenceModel::class)
                val prefs by store
                store.init(LoadStrategy.UseReader(storage), PersistStrategy.Disabled).getOrThrow()
                prefs.shouldHoldLockedValues()
            }
        }

        listOf(ImportStrategy.Merge, ImportStrategy.Erase).forEach { strategy ->
            test("on restore with $strategy, including the legacy app icon key") {
                withPreferenceFile(lockedFile) { storage ->
                    val store = jetprefDataStoreOf(FlorisPreferenceModel::class)
                    val prefs by store
                    store.init(LoadStrategy.Disabled, PersistStrategy.Disabled).getOrThrow()
                    store.import(strategy, storage).getOrThrow()
                    prefs.shouldHoldLockedValues()
                }
            }
        }

        listOf(
            "legacy key first" to listOf("b;advanced__show_app_icon;false", "b;other__show_app_icon;false"),
            "legacy key last" to listOf("b;other__show_app_icon;false", "b;advanced__show_app_icon;false"),
        ).forEach { (label, lines) ->
            test("the app icon stays shown when both keys are stored, $label") {
                withPreferenceFile(lines.joinToString("\r\n", postfix = "\r\n")) { storage ->
                    val store = jetprefDataStoreOf(FlorisPreferenceModel::class)
                    val prefs by store
                    store.init(LoadStrategy.UseReader(storage), PersistStrategy.Disabled).getOrThrow()
                    prefs.other.showAppIcon.get() shouldBe true
                }
            }
        }
    }
})

private suspend fun withPreferenceFile(content: String, block: suspend (FileBasedStorage) -> Unit) {
    val directory = Files.createTempDirectory("ownkey-pref-migrations-")
    val path = directory.resolve("preferences.jetpref")
    try {
        path.toFile().writeText(content)
        block(FileBasedStorage(path.toString()))
    } finally {
        Files.deleteIfExists(path.resolveSibling("preferences.jetpref.tmp"))
        Files.deleteIfExists(path)
        Files.deleteIfExists(directory)
    }
}
