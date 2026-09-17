/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation.dictionary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.app.ownkeyAccentColor
import dev.patrickgold.florisboard.dictationFixController
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing

private val PillShape = RoundedCornerShape(999.dp)
private val CardShape = RoundedCornerShape(10.dp)
private val MinTouchTarget = 48.dp

/** Smartbar pill shown for a short while after dictation inserted text. */
@Composable
fun DictationFixChip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controller = remember(context) { context.dictationFixController().value }
    val state by controller.state.collectAsState()
    if (state !is DictationFixState.Offered) return
    val label = userStringRes(R.string.dictation_fix__chip)
    val accent = ownkeyAccentColor()
    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClickLabel = label, role = Role.Button, onClick = controller::openChooser),
        color = OwnkeyBrand.Glass.Sheet,
        contentColor = OwnkeyBrand.Glass.Ink,
        shape = PillShape,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** Suggestion-strip title while the chooser owns the keyboard area, so predictions do not compete with it. */
@Composable
fun DictationFixSmartbarTitle(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = ownkeyAccentColor(),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = userStringRes(R.string.dictation_fix__chip),
                color = OwnkeyBrand.Bone.copy(alpha = 0.86f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/**
 * Word chooser that replaces the keyboard while the user picks the misheard word. Tapping a
 * neighbouring word grows the selection into a phrase; the action button names the selection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DictationFixPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controller = remember(context) { context.dictationFixController().value }
    val editorInstance by context.editorInstance()
    val state by controller.state.collectAsState()
    val choosing = state as? DictationFixState.Choosing ?: return
    val accent = ownkeyAccentColor()
    val selectedText = choosing.selectedText
    val closeLabel = userStringRes(R.string.dictation_fix__close)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userStringRes(R.string.dictation_fix__chooser_title),
                    color = OwnkeyBrand.Glass.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = userStringRes(R.string.dictation_fix__chooser_hint),
                    color = OwnkeyBrand.Glass.InkSoft,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CloseButton(label = closeLabel, onClick = controller::dismiss)
        }
        FlowRow(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            choosing.tokens.forEachIndexed { index, token ->
                val selected = choosing.selected?.contains(index) == true
                TokenChip(
                    text = token.text,
                    selected = selected,
                    accent = accent,
                    onClick = { controller.tapToken(index) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PanelButton(
                label = userStringRes(R.string.dictation_fix__chooser_settings),
                onClick = controller::openDictionaryForSource,
                primary = false,
                accent = accent,
            )
            Spacer(modifier = Modifier.weight(1f))
            PanelButton(
                label = if (selectedText != null) {
                    userStringRes(R.string.dictation_fix__chooser_action, "word" to selectedText)
                } else {
                    userStringRes(R.string.dictation_fix__chooser_action_none)
                },
                onClick = { controller.beginReplacement(editorInstance.activeContent) },
                primary = true,
                accent = accent,
                enabled = selectedText != null,
            )
        }
    }
}

/**
 * Compact row between the smartbar and the keyboard while the user retypes the word, followed by
 * the confirmation. The keyboard itself stays available, so typing goes to the host editor.
 */
@Composable
fun DictationFixRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controller = remember(context) { context.dictationFixController().value }
    val state by controller.state.collectAsState()
    val current = state
    if (current !is DictationFixState.Replacing && current !is DictationFixState.Saved && current !is DictationFixState.Manual) return
    val accent = ownkeyAccentColor()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = OwnkeyBrand.Glass.Sheet,
        contentColor = OwnkeyBrand.Glass.Ink,
    ) {
        Row(
            modifier = Modifier
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                .heightIn(min = MinTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (current) {
                is DictationFixState.Replacing -> ReplacingContent(current, accent, controller)
                is DictationFixState.Saved -> SavedContent(current)
                is DictationFixState.Manual -> ManualContent(current, accent, controller)
                else -> Unit
            }
        }
    }
}

@Composable
private fun RowScope.ReplacingContent(
    state: DictationFixState.Replacing,
    accent: Color,
    controller: DictationFixController,
) {
    val replacement = state.replacement.trim()
    val addWordLabel = userStringRes(R.string.dictation_fix__row_add_word)
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = if (replacement.isEmpty()) {
                userStringRes(R.string.dictation_fix__row_prompt, "word" to state.source)
            } else {
                userStringRes(R.string.dictation_fix__row_preview, "word" to state.source, "replacement" to replacement)
            },
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .clip(PillShape)
                .clickable(role = Role.Checkbox, onClickLabel = addWordLabel, onClick = controller::toggleAddAsWord)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state.addAsWord,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = accent,
                    uncheckedColor = OwnkeyBrand.Glass.InkSoft,
                    checkmarkColor = OwnkeyBrand.Glass.Ink,
                ),
                modifier = Modifier.size(32.dp),
            )
            Text(text = addWordLabel, color = OwnkeyBrand.Glass.InkSoft, fontSize = 12.sp, maxLines = 1)
        }
    }
    PanelButton(
        label = userStringRes(R.string.dictation_fix__row_save),
        onClick = controller::save,
        primary = true,
        accent = accent,
        enabled = state.canSave,
    )
    CloseButton(label = userStringRes(R.string.dictation_fix__row_dismiss), onClick = controller::dismiss)
}

@Composable
private fun RowScope.SavedContent(state: DictationFixState.Saved) {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = OwnkeyBrand.Glass.Success,
        modifier = Modifier.size(20.dp),
    )
    Text(
        text = if (state.wordAdded) {
            userStringRes(R.string.dictation_fix__saved_with_word, "word" to state.source, "replacement" to state.replacement)
        } else {
            userStringRes(R.string.dictation_fix__saved, "word" to state.source, "replacement" to state.replacement)
        },
        modifier = Modifier.weight(1f),
        fontSize = 13.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RowScope.ManualContent(
    state: DictationFixState.Manual,
    accent: Color,
    controller: DictationFixController,
) {
    Text(
        text = userStringRes(R.string.dictation_fix__manual, "word" to state.source),
        modifier = Modifier.weight(1f),
        fontSize = 13.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    PanelButton(
        label = userStringRes(R.string.dictation_fix__manual_open),
        onClick = controller::openDictionaryForSource,
        primary = true,
        accent = accent,
    )
    CloseButton(label = userStringRes(R.string.dictation_fix__row_dismiss), onClick = controller::dismiss)
}

@Composable
private fun TokenChip(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = MinTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = text },
        color = if (selected) accent else OwnkeyBrand.Glass.Key,
        contentColor = OwnkeyBrand.Glass.Ink,
        shape = CardShape,
        border = if (selected) null else BorderStroke(1.dp, OwnkeyBrand.Glass.Ink.copy(alpha = 0.08f)),
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PanelButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    accent: Color,
    enabled: Boolean = true,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = MinTouchTarget)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        color = if (primary) accent else OwnkeyBrand.Glass.Key,
        contentColor = OwnkeyBrand.Glass.Ink,
        shape = PillShape,
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CloseButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(MinTouchTarget)
            .clip(CircleShape)
            .background(Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = OwnkeyBrand.Glass.Ink.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp),
        )
    }
}
