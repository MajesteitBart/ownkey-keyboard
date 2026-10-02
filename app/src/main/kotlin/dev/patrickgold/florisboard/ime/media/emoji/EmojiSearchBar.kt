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

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.systemService
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/** The results row is a bit taller than the Smartbar, so the emojis in it are easy to hit. */
private const val ResultsRowHeightFactor = 1.25f

/** Emojis found for [query], so results that arrive late are never taken for those of a newer query. */
private data class SearchResults(val query: String, val emojis: List<Emoji>) {
    companion object {
        val None = SearchResults("", emptyList())
    }
}

/** The "Search emoji" field in the emoji palette's bottom row. Tapping it opens [EmojiSearchBar]. */
@Composable
fun EmojiSearchField(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inputFeedbackController = LocalInputFeedbackController.current
    SearchFieldFrame(
        modifier = modifier,
        clickAndSemanticsModifier = Modifier.clickable {
            inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
            onClick()
        },
    ) {
        SearchFieldPlaceholder()
    }
}

/**
 * Shown above the letter keyboard while emoji search is open: the matching emojis, and the query typed so far. The
 * keyboard manager sends typed keys to the query, so nothing reaches the app until an emoji is picked.
 */
@Composable
fun EmojiSearchBar(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()
    val editorInstance by context.editorInstance()
    val inputFeedbackController = LocalInputFeedbackController.current

    val search = keyboardManager.emojiSearch
    val query = search.query

    val subtypes by subtypeManager.subtypesFlow.collectAsState()
    val activeSubtype by subtypeManager.activeSubtypeFlow.collectAsState()
    val locales = remember(subtypes, activeSubtype) {
        activeSubtype.locales() + subtypes.flatMap { it.locales() }
    }
    val index by produceState<EmojiSearchIndex?>(initialValue = null, locales) {
        value = withContext(Dispatchers.Default) { EmojiSearchIndex.forLocales(context, locales) }
    }

    val activeEditorInfo by editorInstance.activeInfoFlow.collectAsState()
    val metadataVersion = activeEditorInfo.emojiCompatMetadataVersion
    val emojiCompatInstance by FlorisEmojiCompat.getAsFlow(activeEditorInfo.emojiCompatReplaceAll).collectAsState()
    val systemFontPaint = remember { Paint().apply { typeface = Typeface.DEFAULT } }
    val preferredSkinTone by prefs.emoji.preferredSkinTone.collectAsState()
    fun canDraw(emoji: Emoji) = emojiCompatInstance.canDraw(emoji, metadataVersion, systemFontPaint)

    // Read once, like the palette's recent tab, so recents do not reorder under the finger as emojis go in
    val recentEmojis = remember(emojiCompatInstance) {
        val deviceLocked = context.systemService(AndroidKeyguardManager::class)
            .let { it.isDeviceLocked || it.isKeyguardLocked }
        if (prefs.emoji.historyEnabled.get() && !deviceLocked) {
            prefs.emoji.historyData.get().let { (it.pinned + it.recent).distinct() }.filter { canDraw(it) }
        } else {
            emptyList()
        }
    }
    val recentValues = remember(recentEmojis) { recentEmojis.map { it.value }.toSet() }
    val matches by produceState(SearchResults.None, index, query, preferredSkinTone, emojiCompatInstance) {
        val currentIndex = index ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val emojis = currentIndex
                .search(query = query, recent = recentValues, isSupported = { canDraw(it) })
                .map { emojiSet ->
                    // The base emoji is drawable, but the chosen skin tone variant may not be
                    emojiSet.base(withSkinTone = preferredSkinTone).takeIf { canDraw(it) } ?: emojiSet.emojis.first()
                }
            SearchResults(query, emojis)
        }
    }
    val hasQuery = query.isNotBlank()
    // Until the search for the latest query is done, the previous results stay visible but enter inserts nothing
    val isCurrent = matches.query == query
    val results = if (hasQuery) matches.emojis else recentEmojis
    SideEffect {
        search.topResult = if (hasQuery && isCurrent) matches.emojis.firstOrNull() else null
    }

    SnyggColumn(
        elementName = FlorisImeUi.Smartbar.elementName,
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight * ResultsRowHeightFactor),
            contentAlignment = Alignment.CenterStart,
        ) {
            val emojiFontSize = with(LocalDensity.current) { (maxHeight * EmojiFontSizeToCellRatio).toSp() }
            val listState = rememberLazyListState()
            LaunchedEffect(results) { if (results.isNotEmpty()) listState.scrollToItem(0) }
            if (results.isEmpty()) {
                val message = when {
                    !hasQuery -> R.string.emoji__search__start_hint
                    index == null || !isCurrent -> null
                    else -> R.string.emoji__search__no_results
                }
                if (message != null) {
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = stringRes(message),
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                ) {
                    items(results, key = { it.value }) { emoji ->
                        SnyggBox(
                            elementName = FlorisImeUi.MediaEmojiKey.elementName,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f)
                                .pointerInput(emoji) {
                                    detectTapGestures(
                                        onPress = { inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED) },
                                        onTap = { keyboardManager.commitEmojiFromSearch(emoji) },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            EmojiText(
                                text = emoji.value,
                                emojiCompatInstance = emojiCompatInstance,
                                fontSize = emojiFontSize,
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val buttonModifier = Modifier
                .sizeIn(maxHeight = FlorisImeSizing.smartbarHeight)
                .aspectRatio(1f)
            SnyggIconButton(
                elementName = FlorisImeUi.SmartbarActionKey.elementName,
                onClick = { keyboardManager.closeEmojiSearch() },
                modifier = buttonModifier,
            ) {
                SnyggIcon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringRes(R.string.emoji__search__close),
                )
            }
            SearchFieldFrame(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    SearchFieldCaret(query)
                    SearchFieldPlaceholder()
                } else {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = query,
                        fontSize = searchFieldFontSize(),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.StartEllipsis,
                    )
                    SearchFieldCaret(query)
                }
            }
            if (query.isNotEmpty()) {
                SnyggIconButton(
                    elementName = FlorisImeUi.SmartbarActionKey.elementName,
                    onClick = { search.clear() },
                    modifier = buttonModifier,
                ) {
                    SnyggIcon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringRes(R.string.emoji__search__clear),
                    )
                }
            } else {
                Box(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun SearchFieldFrame(
    modifier: Modifier = Modifier,
    clickAndSemanticsModifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    SnyggRow(
        elementName = FlorisImeUi.ExtractedLandscapeInputField.elementName,
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 5.dp),
        clickAndSemanticsModifier = clickAndSemanticsModifier.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier
                .padding(end = 6.dp)
                .size(18.dp),
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = LocalContentColor.current.copy(alpha = 0.7f),
        )
        content()
    }
}

@Composable
private fun SearchFieldPlaceholder() {
    Text(
        text = stringRes(R.string.emoji__search__hint),
        color = LocalContentColor.current.copy(alpha = 0.6f),
        fontSize = searchFieldFontSize(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun searchFieldFontSize() =
    rememberSnyggThemeQuery(FlorisImeUi.ExtractedLandscapeInputField.elementName).fontSize(default = 16.sp)

/** A blinking caret that shows where typed letters go. It stays visible while the query changes. */
@Composable
private fun SearchFieldCaret(query: String) {
    val visible by produceState(true, query) {
        value = true
        while (true) {
            delay(530)
            value = !value
        }
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .width(2.dp)
            .height(18.dp)
            .background(if (visible) LocalContentColor.current else Color.Transparent),
    )
}
