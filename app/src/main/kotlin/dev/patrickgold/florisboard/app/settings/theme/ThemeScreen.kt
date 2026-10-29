/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.app.ext.ExtensionListScreenType
import dev.patrickgold.florisboard.app.ui.ChoiceOption
import dev.patrickgold.florisboard.app.ui.OwnkeyChoiceListRow
import dev.patrickgold.florisboard.app.ui.OwnkeyDialog
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.OwnkeySegmented
import dev.patrickgold.florisboard.app.ui.OwnkeySwitchRow
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.theme.ThemeKeyRadius
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.material.ui.ExperimentalJetPrefMaterial3Ui
import dev.patrickgold.jetpref.material.ui.JetPrefColorPicker
import dev.patrickgold.jetpref.material.ui.rememberJetPrefColorPickerState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.OwnkeyTextFieldDefaults
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.contrastRatio
import org.florisboard.lib.compose.ownkeyFocusRing
import org.florisboard.lib.compose.stringRes
import androidx.compose.runtime.collectAsState as collectFlowAsState

private val SwatchCorner = 20.dp
private val SelectionRingWidth = 2.dp
private val SelectionRingGap = 2.dp
private val SelectionInset = SelectionRingWidth + SelectionRingGap
private val AccentCircleSize = 32.dp
private val AccentBoxSize = 48.dp

/** Accent colours below this contrast against Card are hard to see on switches and checkmarks. */
private const val MinAccentContrast = 3.0

@Composable
fun ThemeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__appearance__title)
    previewFieldVisible = true

    val context = LocalContext.current
    val navController = LocalNavController.current

    content {
        val scope = rememberCoroutineScope()
        val theme = prefs.theme
        val mode by theme.mode.collectAsState()
        val dayThemeId by theme.dayThemeId.collectAsState()
        val nightThemeId by theme.nightThemeId.collectAsState()
        val accent by theme.accentColor.collectAsState()
        val appearanceMode = AppearanceMode.of(mode)
        var isCustomColourOpen by remember { mutableStateOf(false) }

        fun apply(writes: ThemeWrites) {
            scope.launch {
                writes.mode?.let { theme.mode.set(it) }
                writes.glassPreset?.let { theme.glassPreset.set(it) }
                writes.dayThemeId?.let { theme.dayThemeId.set(it) }
                writes.nightThemeId?.let { theme.nightThemeId.set(it) }
            }
        }

        OwnkeySegmented(
            modifier = Modifier
                .padding(bottom = OwnkeyTokens.GroupGap)
                .testTag("appearance-mode"),
            options = listOf(
                stringRes(R.string.settings__appearance__mode_dark),
                stringRes(R.string.settings__appearance__mode_light),
                stringRes(R.string.settings__appearance__mode_auto),
            ),
            selectedIndex = appearanceMode.ordinal,
            onSelect = { index -> apply(AppearanceChoices.writesFor(AppearanceMode.entries[index], dayThemeId)) },
        )

        if (appearanceMode != AppearanceMode.LIGHT) {
            OwnkeyGroup {
                DarkStylePicker(
                    selected = DarkStyle.of(nightThemeId),
                    onSelect = { style -> apply(AppearanceChoices.writesFor(style)) },
                )
            }
        }

        val preset = AccentPreset.of(accent)
        OwnkeyGroup {
            AccentPicker(
                accent = accent.takeOrElse { OwnkeyTokens.DefaultAccent },
                preset = preset,
                onSelect = { color -> scope.launch { theme.accentColor.set(color) } },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("appearance-custom-colour"),
                title = stringRes(R.string.settings__appearance__custom_colour),
                onClick = { isCustomColourOpen = true },
            )
        }

        val extensionManager by context.extensionManager()
        val themeExtensions by extensionManager.themes.collectFlowAsState()
        val themeLabels = remember(themeExtensions) {
            themeExtensions.flatMap { ext ->
                // Every theme here is an Ownkey theme, so the prefix only costs room.
                ext.themes.map { ExtensionComponentName(ext.meta.id, it.id) to it.label.removePrefix("Ownkey ") }
            }.toMap()
        }
        OwnkeyGroup(title = stringRes(R.string.settings__appearance__advanced)) {
            OwnkeyRow(
                modifier = Modifier.testTag("appearance-day-theme"),
                title = stringRes(R.string.settings__theme_manager__title_day),
                value = themeLabels[dayThemeId] ?: dayThemeId.componentId,
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.ThemeManager(ThemeManagerScreenAction.SELECT_DAY)) },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("appearance-night-theme"),
                title = stringRes(R.string.settings__theme_manager__title_night),
                value = themeLabels[nightThemeId] ?: nightThemeId.componentId,
                chevron = true,
                onClick = {
                    navController.navigate(Routes.Settings.ThemeManager(ThemeManagerScreenAction.SELECT_NIGHT))
                },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("appearance-custom-themes"),
                title = stringRes(R.string.pref__theme__custom_themes__label),
                summary = stringRes(R.string.settings__appearance__custom_themes_summary),
                chevron = true,
                onClick = { navController.navigate(Routes.Ext.List(ExtensionListScreenType.EXT_THEME, false)) },
            )

            // Key borders and corners only shape the older Glass styles. They show only while changed, and
            // changing them rewrites a Glass theme id to the matching variant.
            val showKeyBorders by theme.showKeyBorders.collectAsState()
            val keyRadius by theme.keyRadius.collectAsState()
            fun applyGlassShape(borders: Boolean, radius: ThemeKeyRadius) {
                scope.launch {
                    theme.showKeyBorders.set(borders)
                    theme.keyRadius.set(radius)
                    glassThemeVariant(dayThemeId, borders, radius)?.let { theme.dayThemeId.set(it) }
                    glassThemeVariant(nightThemeId, borders, radius)?.let { theme.nightThemeId.set(it) }
                }
            }
            if (showKeyBorders != theme.showKeyBorders.default) {
                OwnkeySwitchRow(
                    title = stringRes(R.string.pref__theme__show_key_borders__label),
                    checked = showKeyBorders,
                    onCheckedChange = { applyGlassShape(it, keyRadius) },
                )
            }
            if (keyRadius != theme.keyRadius.default) {
                OwnkeyChoiceListRow(
                    title = stringRes(R.string.pref__theme__key_radius__label),
                    options = enumDisplayEntriesOf(ThemeKeyRadius::class).map { ChoiceOption(it.key, it.label) },
                    selected = keyRadius,
                    onSelect = { applyGlassShape(showKeyBorders, it) },
                )
            }
        }

        if (isCustomColourOpen) {
            CustomAccentDialog(
                initial = accent.takeOrElse { OwnkeyTokens.DefaultAccent },
                onDismiss = { isCustomColourOpen = false },
                onSave = { color -> scope.launch { theme.accentColor.set(color) } },
            )
        }
    }
}

/** A title line inside a card child, matching the rows around it. */
@Composable
private fun CardTitleLine(title: String, value: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OwnkeyTokens.RowPaddingHorizontal)
            .padding(top = OwnkeyTokens.RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = OwnkeyTokens.Label,
        )
        if (value != null) {
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = OwnkeyTokens.Secondary,
                maxLines = 1,
            )
        }
    }
}

/** Draws the shared selection ring: 2dp Label, 2dp gap in the container colour, around [shape]. */
private fun Modifier.selectionRing(selected: Boolean, shape: Shape): Modifier = this.drawBehind {
    if (!selected) return@drawBehind
    val stroke = SelectionRingWidth.toPx()
    val inset = stroke / 2f
    val ringSize = Size(size.width - stroke, size.height - stroke)
    val outline = shape.createOutline(ringSize, layoutDirection, this)
    translate(inset, inset) {
        drawOutline(outline, color = OwnkeyTokens.Label, style = Stroke(width = stroke))
    }
}

@Composable
private fun DarkStylePicker(selected: DarkStyle?, onSelect: (DarkStyle) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(OwnkeyTokens.Card)) {
        CardTitleLine(title = stringRes(R.string.settings__appearance__dark_style))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (style in DarkStyle.entries) {
                val name = stringRes(
                    when (style) {
                        DarkStyle.GRAPHITE -> R.string.settings__appearance__graphite
                        DarkStyle.BLACK -> R.string.settings__appearance__black
                    }
                )
                val isSelected = style == selected
                val outerShape = RoundedCornerShape(SwatchCorner + SelectionInset)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(outerShape)
                        .ownkeyFocusRing(outerShape, inset = true)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(style) })
                        .testTag("appearance-style-${style.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectionRing(isSelected, outerShape)
                            .padding(SelectionInset),
                    ) {
                        KeyboardSwatch(palette = style.palette)
                    }
                    Text(
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) OwnkeyTokens.Label else OwnkeyTokens.Secondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** A small keyboard in the style's colours: three letter rows and a bottom row with space and enter. */
@Composable
private fun KeyboardSwatch(palette: SignalPalette) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.55f)
            .clip(RoundedCornerShape(SwatchCorner))
            .background(palette.background),
    ) {
        drawMiniKeyboard(palette)
    }
}

private fun DrawScope.drawMiniKeyboard(palette: SignalPalette) {
    val pad = 10.dp.toPx()
    val gap = 3.dp.toPx()
    val radius = CornerRadius(2.5.dp.toPx())
    val rows = 4
    val keyHeight = (size.height - 2 * pad - (rows - 1) * gap) / rows
    val unit = (size.width - 2 * pad - 9 * gap) / 10f

    fun key(x: Float, row: Int, units: Float, color: Color) {
        val y = pad + row * (keyHeight + gap)
        val width = units * unit + (units - 1f).coerceAtLeast(0f) * gap
        drawRoundRect(color = color, topLeft = Offset(x, y), size = Size(width, keyHeight), cornerRadius = radius)
    }
    fun advance(units: Float) = units * unit + (units - 1f).coerceAtLeast(0f) * gap + gap

    // Row 1: ten letters. Row 2: nine, centred.
    var x = pad
    repeat(10) { key(x, 0, 1f, palette.surface); x += advance(1f) }
    x = pad + (unit + gap) / 2f
    repeat(9) { key(x, 1, 1f, palette.surface); x += advance(1f) }
    // Row 3: shift, seven letters, delete.
    x = pad
    key(x, 2, 1.5f, palette.surfaceFn); x += advance(1.5f)
    repeat(7) { key(x, 2, 1f, palette.surface); x += advance(1f) }
    key(x, 2, 1.5f, palette.surfaceFn)
    // Row 4: symbols, space, enter.
    x = pad
    key(x, 3, 2f, palette.surfaceFn); x += advance(2f)
    key(x, 3, 6f, palette.surface); x += advance(6f)
    key(x, 3, 2f, palette.enter)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(accent: Color, preset: AccentPreset?, onSelect: (Color) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(OwnkeyTokens.Card)) {
        CardTitleLine(
            title = stringRes(R.string.settings__appearance__accent),
            value = preset?.let { accentName(it) } ?: stringRes(R.string.settings__appearance__custom),
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (entry in AccentPreset.entries) {
                AccentCircle(
                    color = entry.color,
                    name = accentName(entry),
                    selected = entry == preset,
                    onClick = { onSelect(entry.color) },
                    tag = "appearance-accent-${entry.name.lowercase()}",
                )
            }
            if (preset == null) {
                AccentCircle(
                    color = accent,
                    name = stringRes(R.string.settings__appearance__custom),
                    selected = true,
                    onClick = { },
                    tag = "appearance-accent-custom",
                )
            }
        }
    }
}

@Composable
private fun accentName(preset: AccentPreset): String = stringRes(
    when (preset) {
        AccentPreset.EMBER -> R.string.settings__appearance__accent_ember
        AccentPreset.LIME -> R.string.settings__appearance__accent_lime
        AccentPreset.BLUE -> R.string.settings__appearance__accent_blue
        AccentPreset.VIOLET -> R.string.settings__appearance__accent_violet
        AccentPreset.PINK -> R.string.settings__appearance__accent_pink
        AccentPreset.MONO -> R.string.settings__appearance__accent_mono
    }
)

@Composable
private fun AccentCircle(color: Color, name: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    Box(
        modifier = Modifier
            .size(AccentBoxSize)
            .clip(CircleShape)
            .ownkeyFocusRing(CircleShape, inset = true)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(AccentCircleSize + SelectionInset * 2)
                .selectionRing(selected, CircleShape)
                .padding(SelectionInset),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/**
 * Jetpref's colour picker in an Ownkey dialog. Colours too dark to see against Card can't be saved: the
 * accent marks switches, checkmarks and the mic, so it has to stand out.
 */
@OptIn(ExperimentalJetPrefMaterial3Ui::class)
@Composable
private fun CustomAccentDialog(initial: Color, onDismiss: () -> Unit, onSave: (Color) -> Unit) {
    val pickerState = rememberJetPrefColorPickerState(initColor = initial)
    var picked by remember { mutableStateOf(initial) }
    val readable = contrastRatio(picked, OwnkeyTokens.Card) >= MinAccentContrast
    OwnkeyDialog(
        title = stringRes(R.string.settings__appearance__custom_colour),
        onDismiss = onDismiss,
        confirmLabel = stringRes(R.string.action__save),
        confirmEnabled = readable,
        onConfirm = {
            onSave(picked.copy(alpha = 1f))
            onDismiss()
        },
        dismissLabel = stringRes(R.string.action__cancel),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            JetPrefColorPicker(
                onColorChange = { picked = it },
                state = pickerState,
                withAlpha = false,
                editorFieldAppearance = OwnkeyTextFieldDefaults.jetPrefAppearance(),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (!readable) {
            Text(
                text = stringRes(R.string.settings__appearance__custom_colour_too_dark),
                style = MaterialTheme.typography.bodySmall,
                color = OwnkeyTokens.Danger,
            )
        }
    }
}
