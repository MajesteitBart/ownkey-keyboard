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

package dev.patrickgold.florisboard.app.settings.theme

import androidx.compose.ui.graphics.Color
import dev.patrickgold.florisboard.ime.theme.ThemeGlassPreset
import dev.patrickgold.florisboard.ime.theme.ThemeKeyRadius
import dev.patrickgold.florisboard.ime.theme.ThemeMode
import dev.patrickgold.florisboard.ime.theme.extCoreTheme
import dev.patrickgold.florisboard.ime.theme.extMyTheme
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AppearanceChoicesTest : FunSpec({
    val userTheme = ExtensionComponentName("local.themes.mine", "sunset")
    val glassDay = extMyTheme("ownkey_glass_day_borderless_medium")

    test("picking a dark style writes its preset and night theme and nothing else") {
        AppearanceChoices.writesFor(DarkStyle.GRAPHITE) shouldBe
            ThemeWrites(glassPreset = ThemeGlassPreset.SIGNAL, nightThemeId = SignalThemes.Graphite)
        AppearanceChoices.writesFor(DarkStyle.BLACK) shouldBe
            ThemeWrites(glassPreset = ThemeGlassPreset.SIGNAL_BLACK, nightThemeId = SignalThemes.Black)
    }

    test("picking Light or Auto moves an Ownkey day style to Signal Bone") {
        for (mode in listOf(AppearanceMode.LIGHT, AppearanceMode.AUTO)) {
            AppearanceChoices.writesFor(mode, glassDay) shouldBe
                ThemeWrites(mode = mode.mode, dayThemeId = SignalThemes.Bone)
            AppearanceChoices.writesFor(mode, SignalThemes.Graphite) shouldBe
                ThemeWrites(mode = mode.mode, dayThemeId = SignalThemes.Bone)
            AppearanceChoices.writesFor(mode, extMyTheme("voxtral_day")) shouldBe
                ThemeWrites(mode = mode.mode, dayThemeId = SignalThemes.Bone)
        }
    }

    test("picking a mode keeps a day theme the user chose and never rewrites Bone") {
        AppearanceChoices.writesFor(AppearanceMode.LIGHT, userTheme) shouldBe ThemeWrites(mode = ThemeMode.ALWAYS_DAY)
        AppearanceChoices.writesFor(AppearanceMode.LIGHT, extCoreTheme("floris_day")) shouldBe
            ThemeWrites(mode = ThemeMode.ALWAYS_DAY)
        AppearanceChoices.writesFor(AppearanceMode.AUTO, SignalThemes.Bone) shouldBe
            ThemeWrites(mode = ThemeMode.FOLLOW_SYSTEM)
    }

    test("picking Dark only changes the mode") {
        AppearanceChoices.writesFor(AppearanceMode.DARK, glassDay) shouldBe ThemeWrites(mode = ThemeMode.ALWAYS_NIGHT)
    }

    test("the segmented control reads every stored mode") {
        AppearanceMode.of(ThemeMode.ALWAYS_NIGHT) shouldBe AppearanceMode.DARK
        AppearanceMode.of(ThemeMode.ALWAYS_DAY) shouldBe AppearanceMode.LIGHT
        AppearanceMode.of(ThemeMode.FOLLOW_SYSTEM) shouldBe AppearanceMode.AUTO
        AppearanceMode.of(ThemeMode.FOLLOW_TIME) shouldBe AppearanceMode.AUTO
    }

    test("the selected tile follows the night theme id") {
        DarkStyle.of(SignalThemes.Graphite) shouldBe DarkStyle.GRAPHITE
        DarkStyle.of(SignalThemes.Black) shouldBe DarkStyle.BLACK
        DarkStyle.of(SignalThemes.Bone) shouldBe null
        DarkStyle.of(extMyTheme("ownkey_glass_night_borderless_medium")) shouldBe null
        DarkStyle.of(ExtensionComponentName("local.themes.mine", "ownkey_signal_black")) shouldBe null
    }

    test("Home names the dark style, Light, or Auto with its dark style") {
        val bone = SignalThemes.Bone
        AppearanceSummary.of(ThemeMode.ALWAYS_NIGHT, bone, SignalThemes.Graphite) shouldBe AppearanceSummary.GRAPHITE
        AppearanceSummary.of(ThemeMode.ALWAYS_NIGHT, userTheme, SignalThemes.Black) shouldBe AppearanceSummary.BLACK
        AppearanceSummary.of(ThemeMode.ALWAYS_DAY, bone, userTheme) shouldBe AppearanceSummary.LIGHT
        AppearanceSummary.of(ThemeMode.FOLLOW_SYSTEM, bone, SignalThemes.Graphite) shouldBe
            AppearanceSummary.AUTO_GRAPHITE
        AppearanceSummary.of(ThemeMode.FOLLOW_SYSTEM, bone, SignalThemes.Black) shouldBe AppearanceSummary.AUTO_BLACK
    }

    test("Home says Custom when a theme it shows isn't a Signal theme") {
        val bone = SignalThemes.Bone
        AppearanceSummary.of(ThemeMode.ALWAYS_NIGHT, bone, userTheme) shouldBe AppearanceSummary.CUSTOM
        AppearanceSummary.of(ThemeMode.ALWAYS_DAY, glassDay, SignalThemes.Graphite) shouldBe AppearanceSummary.CUSTOM
        AppearanceSummary.of(ThemeMode.FOLLOW_SYSTEM, userTheme, SignalThemes.Graphite) shouldBe
            AppearanceSummary.CUSTOM
        AppearanceSummary.of(ThemeMode.FOLLOW_SYSTEM, bone, userTheme) shouldBe AppearanceSummary.CUSTOM
    }

    test("the default prefs read as Graphite") {
        AppearanceSummary.of(ThemeMode.ALWAYS_NIGHT, SignalThemes.Bone, SignalThemes.Graphite) shouldBe
            AppearanceSummary.GRAPHITE
    }

    test("accent presets match stored colours and an unset accent counts as Ember") {
        AccentPreset.of(Color.Unspecified) shouldBe AccentPreset.EMBER
        AccentPreset.of(Color(0xFFDE5F14)) shouldBe AccentPreset.EMBER
        AccentPreset.of(Color(0xFFFFFFFF)) shouldBe AccentPreset.MONO
        AccentPreset.of(Color(0xFF0A84FF)) shouldBe AccentPreset.BLUE
        AccentPreset.of(Color(0xFF123456)) shouldBe null
    }

    test("Glass ids follow key border and corner changes; other ids stay") {
        glassThemeVariant(extMyTheme("ownkey_glass_slate_borderless_medium"), true, ThemeKeyRadius.LARGE) shouldBe
            extMyTheme("ownkey_glass_slate_bordered_large")
        glassThemeVariant(extMyTheme("ownkey_glass_day_bordered_none"), false, ThemeKeyRadius.SMALL) shouldBe
            extMyTheme("ownkey_glass_day_borderless_small")
        glassThemeVariant(SignalThemes.Graphite, true, ThemeKeyRadius.LARGE) shouldBe null
        glassThemeVariant(extMyTheme("ownkey_liquid_glass"), true, ThemeKeyRadius.LARGE) shouldBe null
        glassThemeVariant(ExtensionComponentName("local.themes.mine", "ownkey_glass_day_bordered_none"), false, ThemeKeyRadius.SMALL) shouldBe
            null
    }
})
