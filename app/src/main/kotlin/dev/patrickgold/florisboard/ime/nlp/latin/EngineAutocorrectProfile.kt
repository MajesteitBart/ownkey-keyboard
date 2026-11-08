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

package dev.patrickgold.florisboard.ime.nlp.latin

import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectSettings
import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectStrength
import org.florisboard.lib.prediction.EngineConfig
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Maps the autocorrect settings and the app profile ([AppSpecificAutocorrectProfilePolicy]) onto the prediction
 * engine's autocorrect thresholds.
 *
 * The engine's defaults are tuned for everyday text (the kbbench dev split, with a keep-rate floor on names, slang
 * and new words) and apply at 100% (the DEFAULT profile, or any profile while app profiles are off). The profile
 * sliders (70% to 130%) move every threshold linearly from the default towards [strictest] at 70% or towards
 * [eagerest] at 130%, so each step of the slider changes how readily words are replaced:
 *
 * | Threshold | 70% | 88% (EMAIL) | 100% | 112% (CHAT) | 130% | Effect |
 * |---|---|---|---|---|---|---|
 * | `minUnknownAutocorrectLength` | 6 | 4 | 3 | 3 | 3 | shorter unknown words (names, codes) stay as typed |
 * | `tauUnknown` | 0.80 | 0.62 | 0.50 | 0.47 | 0.42 | how clear the winner over an unknown word must be |
 * | `tauUnknownShort` | 0.95 | 0.80 | 0.70 | 0.67 | 0.62 | the same for inputs of up to 3 letters |
 * | `deltaKnown` | 6 | 4.8 | 4 | 3.8 | 3.5 | evidence (nats) needed to replace a real word |
 * | `deltaKnownShort` | 7 | 5.8 | 5 | 4.8 | 4.5 | the same for inputs of up to 3 letters |
 * | `deltaInvalid` | 4 | 2.8 | 2 | 1.8 | 1.5 | evidence needed to replace a vocabulary word that is not a valid word |
 * | `deltaInvalidShort` | 8 | 6.8 | 6 | 5.8 | 5.5 | the same for inputs of up to 3 letters |
 * | `deltaKnownLmSlope` | 0 | 0.27 | 0.45 | 0.45 | 0.45 | how much language-model preference relaxes `deltaKnown` |
 * | `deltaKnownFloor` | 4 | 2.8 | 2 | 2 | 2 | the relaxed margin never goes below this |
 * | `reviseMargin` | 8 | 6.8 | 6 | 5.8 | 5.5 | evidence (nats) to change the previous word once the next one is typed |
 * | `reviseMarginShort` | 7.5 | 6.3 | 5.5 | 5.3 | 5 | the same for previous words of up to 3 letters |
 *
 * The strict end switches the context-aware real-word relief off (`deltaKnownLmSlope` 0): with it, the 70% thresholds
 * of the 7 MB n-gram models kept 99.2% of the literal checks instead of 99.6% ("dat is tog" became "tot"; with the
 * large models both keep 99.2%). The eager end keeps the relief as it is.
 *
 * The eager side stays close to the defaults: with the large n-gram models, 112% (CHAT) keeps 95.7% of the literal
 * checks (names, slang, new words; 96.4% at 100%, 99.2% at 70%) and 130% keeps 94.1% (it also turns "a travel
 * vlogger" into "blogger"); with the 7 MB models these were 95.3%, 96.1% and 94.9%, while tau 0.40/0.60 and delta 3/4
 * fell to 94.1% before the word-validity margins, below the 95% floor the defaults were tuned with. The confidence, gap and minimum-length percentages of [HighCertaintyAutocorrectConfig] belong
 * to the frequency-list implementation and are not used here; `highCertaintyAutocorrectEnabled` switches engine
 * autocorrect off entirely. Pure; no Android APIs.
 */
internal object EngineAutocorrectProfile {
    const val MinPercent = 70
    const val MaxPercent = 130

    /**
     * Where each strength level sits on the 70% to 130% scale above; the app profile's aggressiveness then moves it by
     * its distance from 100% (CHAT 112% turns Normal into 112% and Gentle into 92%). Normal is the dev-tuned default.
     */
    fun strengthPercent(strength: AutocorrectStrength): Int = when (strength) {
        AutocorrectStrength.GENTLE -> 80
        AutocorrectStrength.NORMAL -> 100
        AutocorrectStrength.STRONG -> 120
    }

    /** The engine configuration for main's autocorrect [settings] (strength, app profile, on/off) on top of [base]. */
    fun configFor(base: EngineConfig, settings: AutocorrectSettings): EngineConfig {
        val percent = strengthPercent(settings.strength) + (settings.aggressivenessPercent - 100)
        return configFor(
            base = base,
            autocorrectEnabled = settings.enabled,
            profilesEnabled = true,
            aggressivenessPercent = percent,
        )
    }

    /** The thresholds at 70%. */
    fun strictest(base: EngineConfig): EngineConfig = base.copy(
        minUnknownAutocorrectLength = base.minUnknownAutocorrectLength + 3,
        tauUnknown = maxOf(base.tauUnknown, 0.80),
        tauUnknownShort = maxOf(base.tauUnknownShort, 0.95),
        deltaKnown = base.deltaKnown + 2.0,
        deltaKnownShort = base.deltaKnownShort + 2.0,
        deltaInvalid = base.deltaInvalid + 2.0,
        deltaInvalidShort = base.deltaInvalidShort + 2.0,
        deltaKnownLmSlope = 0.0,
        deltaKnownFloor = base.deltaKnownFloor + 2.0,
        reviseMargin = base.reviseMargin + 2.0,
        reviseMarginShort = base.reviseMarginShort + 2.0,
    )

    /** The thresholds at 130%. */
    fun eagerest(base: EngineConfig): EngineConfig = base.copy(
        tauUnknown = base.tauUnknown - 0.08,
        tauUnknownShort = base.tauUnknownShort - 0.08,
        deltaKnown = base.deltaKnown - 0.5,
        deltaKnownShort = base.deltaKnownShort - 0.5,
        deltaInvalid = base.deltaInvalid - 0.5,
        deltaInvalidShort = base.deltaInvalidShort - 0.5,
        reviseMargin = base.reviseMargin - 0.5,
        reviseMarginShort = base.reviseMarginShort - 0.5,
    )

    /**
     * The engine configuration for one request.
     *
     * @param autocorrectEnabled `correction.highCertaintyAutocorrectEnabled` and the field allows autocorrect.
     * @param profilesEnabled `correction.appSpecificAutocorrectProfilesEnabled`.
     * @param aggressivenessPercent The profile's aggressiveness ([AppSpecificAutocorrectProfilePolicy
     *  .profileAggressivenessPercent]; 100 for DEFAULT), clamped to [MinPercent]..[MaxPercent].
     */
    fun configFor(
        base: EngineConfig,
        autocorrectEnabled: Boolean,
        profilesEnabled: Boolean,
        aggressivenessPercent: Int,
    ): EngineConfig {
        if (!autocorrectEnabled) return base.copy(autocorrectEnabled = false)
        val percent = aggressivenessPercent.coerceIn(MinPercent, MaxPercent)
        if (!profilesEnabled || percent == 100) return base
        val target = if (percent < 100) strictest(base) else eagerest(base)
        val t = abs(percent - 100) / (100.0 - MinPercent)
        fun lerp(a: Double, b: Double) = a + t * (b - a)
        return base.copy(
            minUnknownAutocorrectLength = (base.minUnknownAutocorrectLength +
                t * (target.minUnknownAutocorrectLength - base.minUnknownAutocorrectLength)).roundToInt(),
            tauUnknown = lerp(base.tauUnknown, target.tauUnknown),
            tauUnknownShort = lerp(base.tauUnknownShort, target.tauUnknownShort),
            deltaKnown = lerp(base.deltaKnown, target.deltaKnown),
            deltaKnownShort = lerp(base.deltaKnownShort, target.deltaKnownShort),
            deltaInvalid = lerp(base.deltaInvalid, target.deltaInvalid),
            deltaInvalidShort = lerp(base.deltaInvalidShort, target.deltaInvalidShort),
            deltaKnownLmSlope = lerp(base.deltaKnownLmSlope, target.deltaKnownLmSlope),
            deltaKnownFloor = lerp(base.deltaKnownFloor, target.deltaKnownFloor),
            reviseMargin = lerp(base.reviseMargin, target.reviseMargin),
            reviseMarginShort = lerp(base.reviseMarginShort, target.reviseMarginShort),
        )
    }
}
