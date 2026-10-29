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

package dev.patrickgold.florisboard.app.ui

import androidx.compose.ui.graphics.Color
import dev.patrickgold.florisboard.app.apptheme.ownkeyColorScheme
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.contrastRatio
import org.florisboard.lib.compose.onAccentFor

class OwnkeyTokensTest : FunSpec({
    val ember = Color(0xFFDE5F14)
    val lime = Color(0xFFD5FF5F)
    val blue = Color(0xFF0A84FF)
    val violet = Color(0xFFBF5AF2)
    val pink = Color(0xFFFF375F)
    val mono = Color(0xFFFFFFFF)

    context("onAccentFor") {
        listOf("Ember" to ember, "Blue" to blue, "Violet" to violet, "Pink" to pink).forEach { (name, accent) ->
            test("white glyphs on $name") {
                onAccentFor(accent) shouldBe Color.White
            }
        }
        listOf("Lime" to lime, "Mono" to mono).forEach { (name, accent) ->
            test("black glyphs on $name") {
                onAccentFor(accent) shouldBe Color.Black
            }
        }
        test("an unspecified accent behaves like Ember") {
            onAccentFor(Color.Unspecified) shouldBe onAccentFor(ember)
        }
    }

    context("contrastRatio") {
        test("black and white are 21:1 in either order") {
            contrastRatio(Color.White, Color.Black) shouldBe (21.0 plusOrMinus 0.001)
            contrastRatio(Color.Black, Color.White) shouldBe (21.0 plusOrMinus 0.001)
        }
        test("a colour against itself is 1:1") {
            contrastRatio(ember, ember) shouldBe (1.0 plusOrMinus 0.0001)
        }
        test("white on the preset accents matches the spec") {
            contrastRatio(Color.White, ember) shouldBe (3.65 plusOrMinus 0.01)
            contrastRatio(Color.White, blue) shouldBe (3.65 plusOrMinus 0.01)
            contrastRatio(Color.White, violet) shouldBe (3.52 plusOrMinus 0.01)
            contrastRatio(Color.White, pink) shouldBe (3.52 plusOrMinus 0.01)
        }
        test("Secondary passes 4.5:1 on Black and Card") {
            contrastRatio(OwnkeyTokens.Secondary, OwnkeyTokens.Black) shouldBe (7.31 plusOrMinus 0.02)
            contrastRatio(OwnkeyTokens.Secondary, OwnkeyTokens.Card) shouldBe (5.26 plusOrMinus 0.02)
        }
        test("Danger passes 4.5:1 on Card") {
            (contrastRatio(OwnkeyTokens.Danger, OwnkeyTokens.Card) >= 4.5) shouldBe true
        }
    }

    context("ownkeyColorScheme") {
        test("fixed roles are black, Card and Raised") {
            val scheme = ownkeyColorScheme(ember)
            scheme.background shouldBe OwnkeyTokens.Black
            scheme.surface shouldBe OwnkeyTokens.Black
            scheme.surfaceContainerHigh shouldBe OwnkeyTokens.Card
            scheme.surfaceContainerHighest shouldBe OwnkeyTokens.Raised
            scheme.onSurfaceVariant shouldBe OwnkeyTokens.Secondary
            scheme.surfaceTint shouldBe Color.Transparent
        }
        test("primary is the accent and onPrimary follows onAccentFor") {
            ownkeyColorScheme(lime).primary shouldBe lime
            ownkeyColorScheme(lime).onPrimary shouldBe Color.Black
            ownkeyColorScheme(ember).onPrimary shouldBe Color.White
        }
        test("an unspecified accent falls back to Ember") {
            ownkeyColorScheme(Color.Unspecified).primary shouldBe OwnkeyTokens.DefaultAccent
        }
    }
})
