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

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.PreferenceDataEvaluator
import dev.patrickgold.jetpref.datastore.model.PreferenceDataEvaluatorScope
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.DialogPrefStrings
import dev.patrickgold.jetpref.datastore.ui.LocalDefaultDialogPrefStrings
import dev.patrickgold.jetpref.datastore.ui.LocalIconSpaceReserved
import dev.patrickgold.jetpref.datastore.ui.LocalIsPrefEnabled
import dev.patrickgold.jetpref.datastore.ui.LocalIsPrefVisible
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.ownkeyFocusRing
import org.florisboard.lib.compose.withTabularNumbers
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val SliderThumbSize = 24.dp
private val SliderTrackHeight = 6.dp

/** Snaps [value] to the nearest step from [min], inside [min]..[max]. */
internal fun snapSliderValue(value: Float, min: Float, max: Float, step: Float): Float {
    if (step <= 0f) return value.coerceIn(min, max)
    val steps = ((value - min) / step).roundToInt()
    return (min + steps * step).coerceIn(min, max)
}

/** Number of intermediate stops M3's Slider needs for [min]..[max] in [step] increments. */
internal fun sliderSteps(min: Float, max: Float, step: Float): Int {
    if (step <= 0f) return 0
    return (((max - min) / step).roundToInt() - 1).coerceAtLeast(0)
}

private const val ValueSlotMaxChars = 12
private val NumberWithUnit = Regex("""^\S*\d\S*\s\S+$""")

/**
 * True when a slider's value label reads as a phrase ("Items copied within the last 60 s") rather than a
 * value ("60 s", "75%"): longer than [ValueSlotMaxChars] or with a space other than the one between a
 * number and its unit. A phrase goes under the title instead of the narrow value slot, where it would be
 * cut off.
 */
internal fun isPhraseValueLabel(label: String): Boolean {
    val text = label.trim()
    if (text.length > ValueSlotMaxChars) return true
    return text.any { it.isWhitespace() } && !NumberWithUnit.matches(text)
}

/**
 * Inline slider in Accent on a Raised track, no ticks or stop indicator, a round Label thumb. The
 * caller keeps the dragged value locally and persists it in [onValueChangeFinished].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OwnkeySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val disabled = OwnkeyTokens.DisabledAlpha
    val base = OwnkeyTokens.Card
    val colors = SliderDefaults.colors(
        thumbColor = OwnkeyTokens.Label,
        activeTrackColor = accent,
        activeTickColor = Color.Transparent,
        inactiveTrackColor = OwnkeyTokens.Raised,
        inactiveTickColor = Color.Transparent,
        disabledThumbColor = OwnkeyTokens.Label.copy(alpha = disabled).compositeOver(base),
        disabledActiveTrackColor = accent.copy(alpha = disabled).compositeOver(base),
        disabledActiveTickColor = Color.Transparent,
        disabledInactiveTrackColor = OwnkeyTokens.Raised.copy(alpha = disabled).compositeOver(base),
        disabledInactiveTickColor = Color.Transparent,
    )
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.ownkeyFocusRing(),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        thumb = {
            Box(
                modifier = Modifier
                    .size(SliderThumbSize)
                    .background(if (enabled) colors.thumbColor else colors.disabledThumbColor, CircleShape),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(SliderTrackHeight),
                enabled = enabled,
                colors = colors,
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
            )
        },
    )
}

/**
 * One generic implementation behind all 8 public [DialogSliderPreference] overloads. Draws the title
 * with the current value on the right (tabular figures) and an inline slider per pref. While dragging
 * only local state changes; the pref is written, and the preview callback runs, when the drag ends.
 */
@Composable
internal fun <V> OwnkeySliderPreferenceImpl(
    prefs: List<PreferenceData<V>>,
    labels: List<String?>,
    modifier: Modifier,
    iconSpaceReserved: Boolean,
    title: String,
    valueLabel: @Composable (V) -> String,
    summary: (@Composable (List<V>) -> String)?,
    min: V,
    max: V,
    stepIncrement: V,
    onPreviewSelectedValues: List<(V) -> Unit>,
    enabledIf: PreferenceDataEvaluator,
    visibleIf: PreferenceDataEvaluator,
    convertToV: (Float) -> V,
) where V : Number, V : Comparable<V> {
    require(stepIncrement > convertToV(0f)) { "Step increment must be greater than 0!" }
    require(max > min) { "Maximum value ($max) must be greater than minimum value ($min)!" }
    if (!(LocalIsPrefVisible.current && visibleIf(PreferenceDataEvaluatorScope))) return

    val isEnabled = LocalIsPrefEnabled.current && enabledIf(PreferenceDataEvaluatorScope)
    val scope = rememberCoroutineScope()
    val values = prefs.map { it.collectAsState().value }
    val contentAlpha = if (isEnabled) 1f else OwnkeyTokens.DisabledAlpha
    val minF = min.toFloat()
    val maxF = max.toFloat()
    val stepF = stepIncrement.toFloat()
    val steps = sliderSteps(minF, maxF, stepF)
    val isSingle = prefs.size == 1

    CompositionLocalProvider(
        LocalIconSpaceReserved provides iconSpaceReserved,
        LocalIsPrefEnabled provides isEnabled,
        LocalIsPrefVisible provides true,
    ) {
        Column(
            modifier = modifier
                .testTag("pref-${prefs.first().key}")
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(
                    start = OwnkeyTokens.RowPaddingHorizontal,
                    end = OwnkeyTokens.RowPaddingHorizontal,
                    top = OwnkeyTokens.RowPaddingVertical,
                    bottom = 4.dp,
                ),
        ) {
            // Local drag state per slider; null means "show the stored value".
            val dragValues = remember(prefs.size) { List(prefs.size) { mutableStateOf<Float?>(null) } }
            prefs.forEachIndexed { index, _ ->
                LaunchedEffect(values[index]) { dragValues[index].value = null }
            }
            val shownValues = values.mapIndexed { index, v ->
                dragValues[index].value?.let { convertToV(it) } ?: v
            }

            // Decided from the range ends too, so the label doesn't jump between slot and line while dragging.
            val isPhrase = isPhraseValueLabel(valueLabel(min)) || isPhraseValueLabel(valueLabel(max)) ||
                shownValues.any { isPhraseValueLabel(valueLabel(it)) }
            OwnkeyTitleValue(
                title = title,
                value = if (isSingle && !isPhrase) valueLabel(shownValues[0]) else null,
                contentAlpha = contentAlpha,
            )
            if (isSingle && isPhrase) {
                PhraseValueLine(valueLabel(shownValues[0]), contentAlpha)
            }
            // Only show a summary that adds something: jetpref's defaults repeat the value labels.
            val summaryText = summary?.invoke(values)
            val defaultSummary = if (isSingle) {
                valueLabel(values[0])
            } else {
                "${valueLabel(values[0])} / ${valueLabel(values[1])}"
            }
            if (!summaryText.isNullOrBlank() && summaryText != defaultSummary) {
                Text(
                    modifier = Modifier.alpha(contentAlpha),
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnkeyTokens.Secondary,
                )
            }
            prefs.forEachIndexed { index, pref ->
                val label = labels.getOrNull(index)
                val shownLabel = valueLabel(shownValues[index])
                if (!isSingle) {
                    Column(modifier = Modifier.padding(top = if (index == 0) 10.dp else 4.dp)) {
                        OwnkeyTitleValue(
                            title = label.orEmpty(),
                            value = if (isPhrase) null else shownLabel,
                            contentAlpha = contentAlpha,
                            titleStyle = MaterialTheme.typography.bodyMedium,
                            titleColor = OwnkeyTokens.Secondary,
                        )
                        if (isPhrase) {
                            PhraseValueLine(shownLabel, contentAlpha)
                        }
                    }
                }
                val dragState = dragValues[index]
                OwnkeySlider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = if (label != null) "$title, $label" else title
                            stateDescription = shownLabel
                        },
                    value = dragState.value ?: shownValues[index].toFloat(),
                    onValueChange = { raw -> dragState.value = snapSliderValue(raw, minF, maxF, stepF) },
                    onValueChangeFinished = {
                        val finished = dragState.value
                        if (finished != null) {
                            val newValue = convertToV(finished)
                            scope.launch { pref.set(newValue) }
                            onPreviewSelectedValues.getOrNull(index)?.invoke(newValue)
                        }
                    },
                    valueRange = minF..maxF,
                    steps = steps,
                    enabled = isEnabled,
                )
            }
        }
    }
}

/** A phrase-style value label on its own line under the title, in the summary style. */
@Composable
private fun PhraseValueLine(text: String, contentAlpha: Float) {
    Text(
        modifier = Modifier.alpha(contentAlpha),
        text = text,
        style = MaterialTheme.typography.bodyMedium.withTabularNumbers(),
        color = OwnkeyTokens.Secondary,
    )
}

private fun Float.roundToIntSafe(): Int = try {
    roundToInt()
} catch (_: IllegalArgumentException) {
    toInt()
}

private fun Float.roundToLongSafe(): Long = try {
    roundToLong()
} catch (_: IllegalArgumentException) {
    toLong()
}

// The 8 public overloads below keep jetpref 0.3.0's names, parameters, order and defaults.
// `icon` and `dialogStrings` are accepted and ignored; there is no dialog any more.

@Suppress("UNUSED_PARAMETER")
@Composable
fun DialogSliderPreference(
    pref: PreferenceData<Int>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    valueLabel: @Composable (Int) -> String = { it.toString() },
    summary: @Composable (Int) -> String = valueLabel,
    min: Int,
    max: Int,
    stepIncrement: Int,
    onPreviewSelectedValue: (Int) -> Unit = { },
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: PreferenceDataEvaluator = { true },
    visibleIf: PreferenceDataEvaluator = { true },
) {
    OwnkeySliderPreferenceImpl(
        listOf(pref), listOf(null), modifier, iconSpaceReserved, title, valueLabel,
        if (summary === valueLabel) null else ({ v -> summary(v[0]) }), min, max, stepIncrement,
        listOf(onPreviewSelectedValue), enabledIf, visibleIf,
    ) { it.roundToIntSafe() }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun DialogSliderPreference(
    primaryPref: PreferenceData<Int>,
    secondaryPref: PreferenceData<Int>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    primaryLabel: String,
    secondaryLabel: String,
    valueLabel: @Composable (Int) -> String = { it.toString() },
    summary: @Composable (Int, Int) -> String = { p, s -> "${valueLabel(p)} / ${valueLabel(s)}" },
    min: Int,
    max: Int,
    stepIncrement: Int,
    onPreviewSelectedPrimaryValue: (Int) -> Unit = { },
    onPreviewSelectedSecondaryValue: (Int) -> Unit = { },
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: PreferenceDataEvaluator = { true },
    visibleIf: PreferenceDataEvaluator = { true },
) {
    OwnkeySliderPreferenceImpl(
        listOf(primaryPref, secondaryPref), listOf(primaryLabel, secondaryLabel), modifier,
        iconSpaceReserved, title, valueLabel, { v -> summary(v[0], v[1]) }, min, max, stepIncrement,
        listOf(onPreviewSelectedPrimaryValue, onPreviewSelectedSecondaryValue), enabledIf, visibleIf,
    ) { it.roundToIntSafe() }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun DialogSliderPreference(
    pref: PreferenceData<Long>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    valueLabel: @Composable (Long) -> String = { it.toString() },
    summary: @Composable (Long) -> String = valueLabel,
    min: Long,
    max: Long,
    stepIncrement: Long,
    onPreviewSelectedValue: (Long) -> Unit = { },
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: PreferenceDataEvaluator = { true },
    visibleIf: PreferenceDataEvaluator = { true },
) {
    OwnkeySliderPreferenceImpl(
        listOf(pref), listOf(null), modifier, iconSpaceReserved, title, valueLabel,
        if (summary === valueLabel) null else ({ v -> summary(v[0]) }), min, max, stepIncrement,
        listOf(onPreviewSelectedValue), enabledIf, visibleIf,
    ) { it.roundToLongSafe() }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun DialogSliderPreference(
    primaryPref: PreferenceData<Long>,
    secondaryPref: PreferenceData<Long>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    primaryLabel: String,
    secondaryLabel: String,
    valueLabel: @Composable (Long) -> String = { it.toString() },
    summary: @Composable (Long, Long) -> String = { p, s -> "${valueLabel(p)} / ${valueLabel(s)}" },
    min: Long,
    max: Long,
    stepIncrement: Long,
    onPreviewSelectedPrimaryValue: (Long) -> Unit = { },
    onPreviewSelectedSecondaryValue: (Long) -> Unit = { },
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: PreferenceDataEvaluator = { true },
    visibleIf: PreferenceDataEvaluator = { true },
) {
    OwnkeySliderPreferenceImpl(
        listOf(primaryPref, secondaryPref), listOf(primaryLabel, secondaryLabel), modifier,
        iconSpaceReserved, title, valueLabel, { v -> summary(v[0], v[1]) }, min, max, stepIncrement,
        listOf(onPreviewSelectedPrimaryValue, onPreviewSelectedSecondaryValue), enabledIf, visibleIf,
    ) { it.roundToLongSafe() }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun DialogSliderPreference(
    pref: PreferenceData<Double>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    valueLabel: @Composable (Double) -> String = { it.toString() },
    summary: @Composable (Double) -> String = valueLabel,
    min: Double,
    max: Double,
    stepIncrement: Double,
    onPreviewSelectedValue: (Double) -> Unit = { },
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: PreferenceDataEvaluator = { true },
    visibleIf: PreferenceDataEvaluator = { true },
) {
    OwnkeySliderPreferenceImpl(
        listOf(pref), listOf(null), modifier, iconSpaceReserved, title, valueLabel,
        if (summary === valueLabel) null else ({ v -> summary(v[0]) }), min, max, stepIncrement,
        listOf(onPreviewSelectedValue), enabledIf, visibleIf,
    ) { it.toDouble() }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun DialogSliderPreference(
    primaryPref: PreferenceData<Double>,
    secondaryPref: PreferenceData<Double>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    primaryLabel: String,
    secondaryLabel: String,
    valueLabel: @Composable (Double) -> String = { it.toString() },
    summary: @Composable (Double, Double) -> String = { p, s -> "${valueLabel(p)} / ${valueLabel(s)}" },
    min: Double,
    max: Double,
    stepIncrement: Double,
    onPreviewSelectedPrimaryValue: (Double) -> Unit = { },
    onPreviewSelectedSecondaryValue: (Double) -> Unit = { },
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: PreferenceDataEvaluator = { true },
    visibleIf: PreferenceDataEvaluator = { true },
) {
    OwnkeySliderPreferenceImpl(
        listOf(primaryPref, secondaryPref), listOf(primaryLabel, secondaryLabel), modifier,
        iconSpaceReserved, title, valueLabel, { v -> summary(v[0], v[1]) }, min, max, stepIncrement,
        listOf(onPreviewSelectedPrimaryValue, onPreviewSelectedSecondaryValue), enabledIf, visibleIf,
    ) { it.toDouble() }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun DialogSliderPreference(
    pref: PreferenceData<Float>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    valueLabel: @Composable (Float) -> String = { it.toString() },
    summary: @Composable (Float) -> String = valueLabel,
    min: Float,
    max: Float,
    stepIncrement: Float,
    onPreviewSelectedValue: (Float) -> Unit = { },
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: PreferenceDataEvaluator = { true },
    visibleIf: PreferenceDataEvaluator = { true },
) {
    OwnkeySliderPreferenceImpl(
        listOf(pref), listOf(null), modifier, iconSpaceReserved, title, valueLabel,
        if (summary === valueLabel) null else ({ v -> summary(v[0]) }), min, max, stepIncrement,
        listOf(onPreviewSelectedValue), enabledIf, visibleIf,
    ) { it }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun DialogSliderPreference(
    primaryPref: PreferenceData<Float>,
    secondaryPref: PreferenceData<Float>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    primaryLabel: String,
    secondaryLabel: String,
    valueLabel: @Composable (Float) -> String = { it.toString() },
    summary: @Composable (Float, Float) -> String = { p, s -> "${valueLabel(p)} / ${valueLabel(s)}" },
    min: Float,
    max: Float,
    stepIncrement: Float,
    onPreviewSelectedPrimaryValue: (Float) -> Unit = { },
    onPreviewSelectedSecondaryValue: (Float) -> Unit = { },
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: PreferenceDataEvaluator = { true },
    visibleIf: PreferenceDataEvaluator = { true },
) {
    OwnkeySliderPreferenceImpl(
        listOf(primaryPref, secondaryPref), listOf(primaryLabel, secondaryLabel), modifier,
        iconSpaceReserved, title, valueLabel, { v -> summary(v[0], v[1]) }, min, max, stepIncrement,
        listOf(onPreviewSelectedPrimaryValue, onPreviewSelectedSecondaryValue), enabledIf, visibleIf,
    ) { it }
}
