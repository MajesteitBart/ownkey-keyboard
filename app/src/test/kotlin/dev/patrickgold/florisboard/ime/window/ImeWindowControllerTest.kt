/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.window

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntRect
import dev.patrickgold.florisboard.ime.keyboard.SplitLayoutMode
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.first

class ImeWindowControllerTest : FunSpec({
    val tolerance = 1e-3f.dp

    coroutineTestScope = true

    listOf(
        Triple(800, SplitLayoutMode.AUTO, ImeWindowMode.Floating.SPLIT),
        Triple(599, SplitLayoutMode.ALWAYS, ImeWindowMode.Floating.NORMAL),
        Triple(800, SplitLayoutMode.NEVER, ImeWindowMode.Floating.NORMAL),
    ).forEach { (width, splitMode, expected) ->
        test("floating toggle at ${width}dp with $splitMode selects $expected and returns to docked") {
            val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
            prefs.keyboard.splitLayoutMode.set(splitMode)
            val controller = ImeWindowController(prefs, backgroundScope)
            val root = with(Density(1f)) { ImeInsets.Root.of(IntRect(0, 0, width, 1200)) }
            controller.updateRootInsets(root)
            controller.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
            controller.actions.toggleFloatingWindow()
            val config = controller.activeWindowConfig.first { it.mode == ImeWindowMode.FLOATING }
            config.floatingMode shouldBe expected
            val spec = controller.activeWindowSpec.first { it is ImeWindowSpec.Floating }
                .shouldBeInstanceOf<ImeWindowSpec.Floating>()
            if (expected == ImeWindowMode.Floating.SPLIT) {
                spec.props.keyboardWidth shouldBe (width - 24).dp
                spec.props.offsetBottom shouldBe 12.dp
            }
            controller.actions.toggleFloatingWindow()
            controller.activeWindowConfig.first { it.mode == ImeWindowMode.FIXED }.fixedMode shouldBe
                ImeWindowMode.Fixed.NORMAL
        }
    }

    listOf(ImeWindowMode.Floating.SPLIT, ImeWindowMode.Floating.NORMAL).forEach { mode ->
        test("releasing $mode at the bottom preserves split floating and docks compact floating") {
            val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
            prefs.keyboard.splitLayoutMode.set(
                if (mode == ImeWindowMode.Floating.SPLIT) SplitLayoutMode.ALWAYS else SplitLayoutMode.NEVER,
            )
            val controller = ImeWindowController(prefs, backgroundScope)
            val root = with(Density(1f)) { ImeInsets.Root.of(IntRect(0, 0, 800, 1200)) }
            controller.updateRootInsets(root)
            controller.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
            controller.actions.toggleFloatingWindow()
            controller.activeWindowSpec.first { it is ImeWindowSpec.Floating }
            val moved = controller.editor.beginMoveGesture()
                .movedBy(DpOffset(0.dp, 2000.dp), rowCount = 4, smartbarRowCount = 0)
                .shouldBeInstanceOf<ImeWindowSpec.Floating>()
            moved.props.offsetBottom shouldBe 0.dp
            moved.shouldDockOnRelease shouldBe (mode == ImeWindowMode.Floating.NORMAL)
            controller.editor.endMoveGesture(moved)
            if (mode == ImeWindowMode.Floating.SPLIT) {
                val saved = controller.activeWindowConfig.first { it.floatingProps[mode]?.offsetBottom == 0.dp }
                saved.mode shouldBe ImeWindowMode.FLOATING
                prefs.keyboard.windowConfig.get()[root.formFactor.typeGuess]?.floatingProps?.get(mode)
                    ?.offsetBottom shouldBe 0.dp
                controller.actions.toggleFloatingWindow()
                controller.activeWindowConfig.first { it.mode == ImeWindowMode.FIXED }
                controller.actions.toggleFloatingWindow()
                controller.activeWindowSpec.first { it is ImeWindowSpec.Floating }
                    .shouldBeInstanceOf<ImeWindowSpec.Floating>().props.offsetBottom shouldBe 0.dp
            } else {
                controller.activeWindowConfig.first { it.mode == ImeWindowMode.FIXED }
            }
        }
    }

    test("voice only replaces the keyboard where allowed and leaving it restores the floating keyboard") {
        val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
        val controller = ImeWindowController(prefs, backgroundScope)
        val root = with(Density(1f)) { ImeInsets.Root.of(IntRect(0, 0, 800, 1200)) }
        controller.updateRootInsets(root)
        controller.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
        controller.actions.toggleFloatingWindow()
        controller.activeWindowConfig.first { it.mode == ImeWindowMode.FLOATING }

        controller.actions.toggleVoiceOnly()
        controller.isVoiceOnlyActive.first { it }
        // A password or incognito field shows the keyboard without forgetting the choice.
        controller.updateVoiceOnlyAllowed(false)
        controller.isVoiceOnlyActive.first { !it }
        prefs.keyboard.voiceOnly.get() shouldBe true
        controller.updateVoiceOnlyAllowed(true)
        controller.isVoiceOnlyActive.first { it }

        val moved = ImeWindowConfig.VoiceBarOffset(x = -40f, y = -300f)
        controller.actions.moveVoiceBar(moved)
        val config = controller.activeWindowConfig.first { it.voiceBarOffset == moved }
        controller.actions.toggleVoiceOnly()
        controller.isVoiceOnlyActive.first { !it }
        prefs.keyboard.voiceOnly.get() shouldBe false
        config.mode shouldBe ImeWindowMode.FLOATING
        prefs.keyboard.windowConfig.get()[root.formFactor.typeGuess]?.voiceBarOffset shouldBe moved
    }

    test("voice only survives rotation while the bar position stays per form factor") {
        val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
        val controller = ImeWindowController(prefs, backgroundScope)
        val portrait = with(Density(1f)) { ImeInsets.Root.of(IntRect(0, 0, 400, 800)) }
        val landscape = with(Density(1f)) { ImeInsets.Root.of(IntRect(0, 0, 800, 400)) }
        portrait.formFactor.typeGuess shouldBe ImeFormFactor.Type.PHONE_PORTRAIT
        landscape.formFactor.typeGuess shouldBe ImeFormFactor.Type.PHONE_LANDSCAPE
        controller.updateRootInsets(portrait)
        controller.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }

        controller.actions.toggleVoiceOnly()
        controller.isVoiceOnlyActive.first { it }
        val moved = ImeWindowConfig.VoiceBarOffset(x = 20f, y = -120f)
        controller.actions.moveVoiceBar(moved)
        controller.activeWindowConfig.first { it.voiceBarOffset == moved }

        controller.updateRootInsets(landscape)
        controller.activeWindowConfig.first { it.voiceBarOffset == ImeWindowConfig.VoiceBarOffset.Zero }
        controller.isVoiceOnlyActive.value shouldBe true

        controller.updateRootInsets(portrait)
        controller.activeWindowConfig.first { it.voiceBarOffset == moved }
        controller.isVoiceOnlyActive.value shouldBe true
    }

    test("window configs saved before voice only still load") {
        val saved = """{"TABLET_PORTRAIT":{"mode":"FLOATING","floatingMode":"SPLIT"}}"""
        val config = ImeWindowConfig.ByTypeSerializer.deserialize(saved)[ImeFormFactor.Type.TABLET_PORTRAIT]
        config?.mode shouldBe ImeWindowMode.FLOATING
        config?.voiceBarOffset shouldBe ImeWindowConfig.VoiceBarOffset.Zero
    }

    test("window configs with the earlier per-form-factor voice only flag keep their sizes and bar position") {
        val saved = """{"TABLET_PORTRAIT":{"mode":"FLOATING","floatingMode":"SPLIT",""" +
            """"floatingProps":{"SPLIT":{"keyboardHeight":300.0,"keyboardWidth":700.0,""" +
            """"offsetLeft":12.0,"offsetBottom":40.0}},"voiceOnly":true,"voiceBarOffset":{"x":10.0,"y":-50.0}}}"""
        val config = ImeWindowConfig.ByTypeSerializer.deserialize(saved)[ImeFormFactor.Type.TABLET_PORTRAIT]
        config?.mode shouldBe ImeWindowMode.FLOATING
        config?.floatingProps?.get(ImeWindowMode.Floating.SPLIT)?.offsetBottom shouldBe 40.dp
        config?.voiceBarOffset shouldBe ImeWindowConfig.VoiceBarOffset(x = 10f, y = -50f)
    }

    test("the floating keyboard follows the split keyboard setting without re-entering floating mode") {
        val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
        prefs.keyboard.splitLayoutMode.set(SplitLayoutMode.AUTO)
        val controller = ImeWindowController(prefs, backgroundScope)
        val root = with(Density(1f)) { ImeInsets.Root.of(IntRect(0, 0, 800, 1200)) }
        controller.updateRootInsets(root)
        controller.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
        controller.actions.toggleFloatingWindow()
        controller.activeWindowSpec.first { (it as? ImeWindowSpec.Floating)?.floatingMode == ImeWindowMode.Floating.SPLIT }

        prefs.keyboard.splitLayoutMode.set(SplitLayoutMode.NEVER)
        val normal = controller.activeWindowSpec
            .first { (it as? ImeWindowSpec.Floating)?.floatingMode == ImeWindowMode.Floating.NORMAL }
            .shouldBeInstanceOf<ImeWindowSpec.Floating>()
        normal.props.shouldBeConstrainedTo(ImeWindowConstraints.of(root, ImeWindowMode.Floating.NORMAL), tolerance)

        prefs.keyboard.splitLayoutMode.set(SplitLayoutMode.ALWAYS)
        controller.activeWindowSpec.first { (it as? ImeWindowSpec.Floating)?.floatingMode == ImeWindowMode.Floating.SPLIT }
    }

    context("isWindowShown state") {
        test("simple onShown onHidden") {
            val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
            val windowController = ImeWindowController(prefs, backgroundScope)

            windowController.isWindowShown.value shouldBe false
            windowController.onWindowShown() shouldBe true
            windowController.isWindowShown.value shouldBe true
            windowController.onWindowHidden() shouldBe true
            windowController.isWindowShown.value shouldBe false
        }

        test("duplicate onWindowShown is detected") {
            val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
            val windowController = ImeWindowController(prefs, backgroundScope)

            windowController.isWindowShown.value shouldBe false
            windowController.onWindowShown() shouldBe true
            windowController.isWindowShown.value shouldBe true
            windowController.onWindowShown().shouldBe(false, "duplicate onWindowShown is detected")
            windowController.isWindowShown.value shouldBe true
        }

        test("duplicate onWindowHidden is detected") {
            val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
            val windowController = ImeWindowController(prefs, backgroundScope)

            windowController.isWindowShown.value shouldBe false
            windowController.onWindowShown() shouldBe true
            windowController.isWindowShown.value shouldBe true
            windowController.onWindowHidden() shouldBe true
            windowController.isWindowShown.value shouldBe false
            windowController.onWindowHidden().shouldBe(false, "duplicate onWindowHidden should fail")
            windowController.isWindowShown.value shouldBe false
        }
    }

    context("for all root insets") {
        test("for all fixed window configs in prefs") {
            checkAll(
                Arb.rootInsets(),
                Arb.windowConfigFixed(),
            ) { rootInsets, windowConfig ->
                val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
                prefs.keyboard.windowConfig.set(mapOf(rootInsets.formFactor.typeGuess to windowConfig))
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)

                val spec = windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }

                assertSoftly {
                    val constraints = ImeWindowConstraints.of(rootInsets, windowConfig.fixedMode)
                    val spec = spec.shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                    spec.props.shouldBeConstrainedTo(constraints, tolerance)
                }
            }
        }

        test("for all floating window configs in prefs") {
            checkAll(
                Arb.rootInsets(),
                Arb.windowConfigFloating(),
            ) { rootInsets, windowConfig ->
                val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
                prefs.keyboard.windowConfig.set(mapOf(rootInsets.formFactor.typeGuess to windowConfig))
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)

                val spec = windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }

                assertSoftly {
                    // The sub-mode follows the split keyboard setting and root width, not the stored field.
                    val spec = spec.shouldBeInstanceOf<ImeWindowSpec.Floating>()
                    val constraints = ImeWindowConstraints.of(rootInsets, spec.floatingMode)
                    spec.props.shouldBeConstrainedTo(constraints, tolerance)
                }
            }
        }
    }
})
