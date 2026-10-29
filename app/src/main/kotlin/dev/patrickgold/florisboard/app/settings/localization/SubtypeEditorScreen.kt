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

package dev.patrickgold.florisboard.app.settings.localization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.ui.LazyCardHeader
import dev.patrickgold.florisboard.app.ui.LazyCardRow
import dev.patrickgold.florisboard.app.ui.OwnkeyButton
import dev.patrickgold.florisboard.app.ui.OwnkeyButtonStyle
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyNotice
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.OwnkeyTextField
import dev.patrickgold.florisboard.app.ui.OwnkeyTone
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.core.SubtypeJsonConfig
import dev.patrickgold.florisboard.ime.core.SubtypeLayoutMap
import dev.patrickgold.florisboard.ime.core.SubtypeNlpProviderMap
import dev.patrickgold.florisboard.ime.core.SubtypePreset
import dev.patrickgold.florisboard.ime.keyboard.LayoutArrangementComponent
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.ime.keyboard.extCorePopupMapping
import dev.patrickgold.florisboard.ime.nlp.han.HanShapeBasedLanguageProvider
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.LocalOwnkeyContentPadding
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.material.ui.JetPrefDropdown
import org.florisboard.lib.compose.FlorisButtonBar
import org.florisboard.lib.compose.FlorisDropdownLikeButton
import org.florisboard.lib.compose.OwnkeyTextFieldDefaults
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.stringRes

private val SelectComponentName = ExtensionComponentName("00", "00")
private val SelectNlpProviderId = SelectComponentName.toString()
private val SelectLayoutMap = SubtypeLayoutMap(
    characters = SelectComponentName,
    symbols = SelectComponentName,
    symbols2 = SelectComponentName,
    numeric = SelectComponentName,
    numericAdvanced = SelectComponentName,
    numericRow = SelectComponentName,
    phone = SelectComponentName,
    phone2 = SelectComponentName,
)
private val SelectLocale = FlorisLocale.from("00", "00")
private val SelectListKeys = listOf(SelectComponentName)

private class SubtypeEditorState(init: Subtype?) {
    companion object {
        val Saver = Saver<SubtypeEditorState, String>(
            save = { editor -> SubtypeJsonConfig.encodeToString(editor.snapshot()) },
            restore = { str ->
                val subtype = SubtypeJsonConfig.decodeFromString<Subtype>(str)
                SubtypeEditorState(subtype)
            },
        )
    }

    val id: MutableState<Long> = mutableLongStateOf(init?.id ?: -1)
    val primaryLocale: MutableState<FlorisLocale> = mutableStateOf(init?.primaryLocale ?: SelectLocale)
    val secondaryLocales: MutableState<List<FlorisLocale>> = mutableStateOf(init?.secondaryLocales ?: listOf())
    val nlpProviders: MutableState<SubtypeNlpProviderMap> = mutableStateOf(init?.nlpProviders ?: Subtype.DEFAULT.nlpProviders)
    val composer: MutableState<ExtensionComponentName> = mutableStateOf(init?.composer ?: SelectComponentName)
    val currencySet: MutableState<ExtensionComponentName> = mutableStateOf(init?.currencySet ?: SelectComponentName)
    val punctuationRule: MutableState<ExtensionComponentName> = mutableStateOf(init?.punctuationRule ?: Subtype.DEFAULT.punctuationRule)
    val popupMapping: MutableState<ExtensionComponentName> = mutableStateOf(init?.popupMapping ?: SelectComponentName)
    val layoutMap: MutableState<SubtypeLayoutMap> = mutableStateOf(init?.layoutMap ?: SelectLayoutMap)

    /** The current values without validation; unset fields still hold the select placeholder. */
    fun snapshot() = Subtype(
        id = id.value,
        primaryLocale = primaryLocale.value,
        secondaryLocales = secondaryLocales.value,
        nlpProviders = nlpProviders.value,
        composer = composer.value,
        currencySet = currencySet.value,
        punctuationRule = punctuationRule.value,
        popupMapping = popupMapping.value,
        layoutMap = layoutMap.value,
    )

    fun applySubtype(subtype: Subtype) {
        id.value = subtype.id
        primaryLocale.value = subtype.primaryLocale
        secondaryLocales.value = subtype.secondaryLocales
        composer.value = subtype.composer
        nlpProviders.value = subtype.nlpProviders
        currencySet.value = subtype.currencySet
        punctuationRule.value = subtype.punctuationRule
        popupMapping.value = subtype.popupMapping
        layoutMap.value = subtype.layoutMap
    }

    fun toSubtype() = runCatching {
        check(primaryLocale.value != SelectLocale)
        check(nlpProviders.value.spelling != SelectNlpProviderId)
        check(nlpProviders.value.suggestion != SelectNlpProviderId)
        check(composer.value != SelectComponentName)
        check(currencySet.value != SelectComponentName)
        check(punctuationRule.value != SelectComponentName)
        check(popupMapping.value != SelectComponentName)
        check(layoutMap.value.characters != SelectComponentName)
        check(layoutMap.value.symbols != SelectComponentName)
        check(layoutMap.value.symbols2 != SelectComponentName)
        check(layoutMap.value.numeric != SelectComponentName)
        check(layoutMap.value.numericAdvanced != SelectComponentName)
        check(layoutMap.value.numericRow != SelectComponentName)
        check(layoutMap.value.phone != SelectComponentName)
        check(layoutMap.value.phone2 != SelectComponentName)
        snapshot()
    }
}

/**
 * `Settings.SubtypeAdd` opens the Add language list; `Settings.SubtypeEdit` opens the editor. Languages
 * with a preset are added from the list; [NewLanguageScreen] sets up any other language by hand.
 */
@Composable
fun SubtypeEditorScreen(id: Long?) {
    if (id == null) {
        AddLanguageScreen()
    } else {
        LanguageEditorScreen(id)
    }
}

/** `Settings.SubtypeNew`: the editor for a language without a preset; Save adds it. */
@Composable
fun NewLanguageScreen() {
    LanguageEditorScreen(id = null)
}

@Composable
private fun AddLanguageScreen() = FlorisScreen {
    title = stringRes(R.string.settings__languages__add)
    scrollable = false

    val prefs by FlorisPreferenceStore
    val navController = LocalNavController.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()

    content {
        val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
        val subtypePresets by keyboardManager.resources.subtypePresets.collectAsState()
        val layouts by keyboardManager.resources.layouts.collectAsState()
        val subtypes by subtypeManager.subtypesFlow.collectAsState()
        var query by rememberSaveable { mutableStateOf("") }

        val phoneLocales = remember(configuration) {
            val localeList = configuration.locales
            List(localeList.size()) { FlorisLocale.from(localeList.get(it)) }
        }
        val sections = remember(subtypePresets, phoneLocales, query, displayLanguageNamesIn) {
            sectionPresets(
                presets = subtypePresets,
                localeOf = { it.locale },
                namesOf = { preset ->
                    listOf(
                        preset.locale.nameFor(displayLanguageNamesIn),
                        preset.locale.displayName(preset.locale),
                        preset.locale.displayName(FlorisLocale.ENGLISH),
                    )
                },
                phoneLocales = phoneLocales,
                query = query,
            )
        }
        val addedLabel = stringRes(R.string.settings__languages__added)

        @Composable
        fun PresetRow(preset: SubtypePreset, index: Int, count: Int, tagPrefix: String) {
            val isAdded = subtypes.any {
                it.primaryLocale == preset.locale && it.layoutMap.characters == preset.preferred.characters
            }
            val name = preset.locale.nameFor(displayLanguageNamesIn)
            LazyCardRow(index = index, count = count) {
                OwnkeyRow(
                    modifier = Modifier.testTag("$tagPrefix-$index"),
                    title = name,
                    summary = layouts[LayoutType.CHARACTERS]?.get(preset.preferred.characters)?.label
                        ?: preset.preferred.characters.componentId,
                    value = if (isAdded) addedLabel else null,
                    onClick = if (isAdded) {
                        null
                    } else {
                        {
                            // Returns false when an identical language already exists; either way it's listed.
                            subtypeManager.addSubtype(preset.toSubtype())
                            navController.popBackStack()
                        }
                    },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = LocalOwnkeyContentPadding.current,
        ) {
            item {
                OwnkeyTextField(
                    modifier = Modifier
                        .padding(bottom = OwnkeyTokens.GroupGap)
                        .testTag("add-language-search"),
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringRes(R.string.settings__localization__subtype_search_locale_placeholder),
                    containerColor = OwnkeyTokens.Card,
                    trailing = {
                        Icon(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(24.dp),
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = OwnkeyTokens.Secondary,
                        )
                    },
                )
            }
            if (sections.suggested.isNotEmpty()) {
                item { LazyCardHeader(stringRes(R.string.settings__languages__on_this_phone), first = true) }
                itemsIndexed(sections.suggested) { index, preset ->
                    PresetRow(preset, index, sections.suggested.size, "add-language-suggested")
                }
            }
            if (sections.others.isNotEmpty()) {
                item {
                    LazyCardHeader(
                        stringRes(R.string.settings__languages__all_languages),
                        first = sections.suggested.isEmpty(),
                    )
                }
                itemsIndexed(sections.others) { index, preset ->
                    PresetRow(preset, index, sections.others.size, "add-language-other")
                }
            }
            if (sections.suggested.isEmpty() && sections.others.isEmpty()) {
                item {
                    OwnkeyNotice(
                        modifier = Modifier.testTag("add-language-no-match"),
                        text = stringRes(
                            R.string.settings__localization__subtype_search_locale_not_found,
                            "search_term" to query,
                        ),
                        actionLabel = stringRes(R.string.settings__languages__set_up_other),
                        onAction = { navController.navigate(Routes.Settings.SubtypeNew) },
                    )
                }
            } else {
                // Languages without a preset are set up by hand; this row stays last so it never hides a match.
                item { Spacer(modifier = Modifier.size(OwnkeyTokens.GroupGap)) }
                item {
                    LazyCardRow(index = 0, count = 1) {
                        OwnkeyRow(
                            modifier = Modifier.testTag("add-language-manual"),
                            title = stringRes(R.string.settings__languages__set_up_other),
                            summary = stringRes(R.string.settings__languages__set_up_other_summary),
                            chevron = true,
                            onClick = { navController.navigate(Routes.Settings.SubtypeNew) },
                        )
                    }
                }
            }
        }
    }
}

/** Edits the language with [id], or sets up a new one when [id] is null. */
@Composable
private fun LanguageEditorScreen(id: Long?) = FlorisScreen {
    title = stringRes(if (id == null) R.string.settings__languages__new_title else R.string.settings__languages__edit_title)

    val selectValue = stringRes(R.string.settings__languages__not_set)
    val selectListValues = remember(selectValue) { listOf(selectValue) }

    val prefs by FlorisPreferenceStore
    val navController = LocalNavController.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()

    val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
    val composers by keyboardManager.resources.composers.collectAsState()
    val currencySets by keyboardManager.resources.currencySets.collectAsState()
    val layoutExtensions by keyboardManager.resources.layouts.collectAsState()
    val popupMappings by keyboardManager.resources.popupMappings.collectAsState()

    val subtypeEditor = rememberSaveable(saver = SubtypeEditorState.Saver) {
        SubtypeEditorState(id?.let { subtypeManager.getSubtypeById(it) })
    }
    var primaryLocale by subtypeEditor.primaryLocale
    var composer by subtypeEditor.composer
    var currencySet by subtypeEditor.currencySet
    var popupMapping by subtypeEditor.popupMapping
    var layoutMap by subtypeEditor.layoutMap
    var nlpProviders by subtypeEditor.nlpProviders

    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showSelectAsError by rememberSaveable { mutableStateOf(false) }
    var showAlreadyAdded by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val selectLocaleScreenResult = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getLiveData<String>(SelectLocaleScreenResultLanguageTag)
    DisposableEffect(selectLocaleScreenResult, lifecycleOwner) {
        val observer = Observer<String> { languageTag ->
            val locale = FlorisLocale.fromTag(languageTag)
            val preset = subtypeManager.getSubtypePresetForLocale(locale)
            primaryLocale = locale
            popupMapping = preset?.popupMapping ?: extCorePopupMapping("default")
            val filled = fillUnsetSubtypeFields(
                current = subtypeEditor.snapshot(),
                fallback = preset?.toSubtype() ?: Subtype.DEFAULT,
                unset = SelectComponentName,
            )
            subtypeEditor.applySubtype(filled)
        }
        selectLocaleScreenResult?.observe(lifecycleOwner, observer)
        onDispose { selectLocaleScreenResult?.removeObserver(observer) }
    }

    @Composable
    fun LayoutField(title: String, layoutType: LayoutType) {
        SubtypeProperty(title) {
            SubtypeLayoutDropdown(
                layoutType = layoutType,
                layouts = layoutExtensions[layoutType] ?: mapOf(),
                showSelectAsError = showSelectAsError,
                layoutMap = layoutMap,
                onLayoutMapChanged = { layoutMap = it },
                selectListValues = selectListValues,
            )
        }
    }

    bottomBar {
        FlorisButtonBar {
            if (id != null) {
                OwnkeyButton(
                    modifier = Modifier.testTag("subtype-delete"),
                    label = stringRes(R.string.action__delete),
                    style = OwnkeyButtonStyle.Destructive,
                    onClick = { showDeleteDialog = true },
                )
            }
            ButtonBarSpacer()
            OwnkeyButton(
                modifier = Modifier.testTag("subtype-save"),
                label = stringRes(R.string.action__save),
                onClick = {
                    subtypeEditor.toSubtype().onSuccess { subtype ->
                        showSelectAsError = false
                        if (id != null) {
                            subtypeManager.modifySubtypeWithSameId(subtype)
                            navController.popBackStack()
                        } else if (subtypeManager.addSubtype(subtype)) {
                            // Back to Languages, past Add language; a deep link may have opened this screen alone.
                            if (!navController.popBackStack(Routes.Settings.Localization, inclusive = false)) {
                                navController.popBackStack()
                            }
                        } else {
                            showAlreadyAdded = true
                        }
                    }.onFailure {
                        showAlreadyAdded = false
                        showSelectAsError = true
                        showAdvanced = true
                    }
                },
            )
        }
    }

    content {
        if (showSelectAsError) {
            OwnkeyNotice(
                text = stringRes(R.string.settings__languages__fields_missing),
                tone = OwnkeyTone.Error,
            )
        }
        if (showAlreadyAdded) {
            OwnkeyNotice(
                modifier = Modifier.testTag("subtype-already-added"),
                text = stringRes(R.string.settings__languages__already_added),
                tone = OwnkeyTone.Error,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SubtypeProperty(stringRes(R.string.settings__languages__language)) {
                FlorisDropdownLikeButton(
                    modifier = Modifier.testTag("subtype-language"),
                    item = if (primaryLocale == SelectLocale) {
                        selectValue
                    } else {
                        primaryLocale.nameFor(displayLanguageNamesIn)
                    },
                    isError = showSelectAsError && primaryLocale == SelectLocale,
                    onClick = {
                        navController.navigate(Routes.Settings.SelectLocale)
                    },
                )
            }
            LayoutField(stringRes(R.string.settings__languages__layout), LayoutType.CHARACTERS)
        }
        Spacer(modifier = Modifier.size(OwnkeyTokens.GroupGap))

        val advancedState = stringRes(
            if (showAdvanced) R.string.settings__languages__expanded else R.string.settings__languages__collapsed
        )
        OwnkeyGroup {
            OwnkeyRow(
                modifier = Modifier
                    .testTag("subtype-advanced")
                    .semantics {
                        stateDescription = advancedState
                    },
                title = stringRes(R.string.settings__languages__group_advanced),
                summary = stringRes(R.string.settings__languages__advanced_summary),
                onClick = { showAdvanced = !showAdvanced },
                trailing = {
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = OwnkeyTokens.Secondary,
                    )
                },
            )
        }

        if (showAdvanced) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SubtypeProperty(stringRes(R.string.settings__languages__accents)) {
                    val popupMappingIds = remember(popupMappings) { SelectListKeys + popupMappings.keys }
                    val popupMappingLabels = remember(popupMappings, selectListValues) {
                        selectListValues + popupMappings.values.map { it.label }
                    }
                    val expanded = remember { mutableStateOf(false) }
                    val selectedIndex = popupMappingIds.indexOf(popupMapping).coerceAtLeast(0)
                    JetPrefDropdown(
                        options = popupMappingLabels,
                        expanded = expanded,
                        selectedOptionIndex = selectedIndex,
                        isError = showSelectAsError && selectedIndex == 0,
                        onSelectOption = { popupMapping = popupMappingIds[it] },
                        appearance = OwnkeyTextFieldDefaults.jetPrefAppearance(),
                    )
                }
                SubtypeProperty(stringRes(R.string.settings__localization__subtype_suggestion_provider)) {
                    // TODO: Put this map somewhere more formal (another KeyboardExtension field?)
                    //  optionally use a string resource below
                    val nlpProviderMappings = mapOf(
                        LatinLanguageProvider.ProviderId to "Latin",
                        HanShapeBasedLanguageProvider.ProviderId to "Chinese shape-based"
                    )
                    val nlpProviderMappingIds = remember(nlpProviderMappings) {
                        listOf(SelectNlpProviderId) + nlpProviderMappings.keys
                    }
                    val nlpProviderMappingLabels = remember(nlpProviderMappings, selectListValues) {
                        selectListValues + nlpProviderMappings.values.map { it }
                    }
                    val expanded = remember { mutableStateOf(false) }
                    val selectedIndex = nlpProviderMappingIds.indexOf(nlpProviders.suggestion).coerceAtLeast(0)
                    JetPrefDropdown(
                        options = nlpProviderMappingLabels,
                        expanded = expanded,
                        selectedOptionIndex = selectedIndex,
                        isError = showSelectAsError && selectedIndex == 0,
                        onSelectOption = {
                            nlpProviders = SubtypeNlpProviderMap(
                                suggestion = nlpProviderMappingIds[it],
                                spelling = nlpProviderMappingIds[it],
                            )
                        },
                        appearance = OwnkeyTextFieldDefaults.jetPrefAppearance(),
                    )
                }
                LayoutField(stringRes(R.string.settings__localization__subtype_symbols_layout), LayoutType.SYMBOLS)
                LayoutField(stringRes(R.string.settings__localization__subtype_symbols2_layout), LayoutType.SYMBOLS2)
                SubtypeProperty(stringRes(R.string.settings__localization__subtype_composer)) {
                    val composerIds = remember(composers) { SelectListKeys + composers.keys }
                    val composerNames = remember(composers, selectListValues) {
                        selectListValues + composers.values.map { it.label }
                    }
                    val expanded = remember { mutableStateOf(false) }
                    JetPrefDropdown(
                        options = composerNames,
                        expanded = expanded,
                        selectedOptionIndex = composerIds.indexOf(composer).coerceAtLeast(0),
                        isError = showSelectAsError && composer == SelectComponentName,
                        onSelectOption = { composer = composerIds[it] },
                        appearance = OwnkeyTextFieldDefaults.jetPrefAppearance(),
                    )
                }
                SubtypeProperty(stringRes(R.string.settings__localization__subtype_currency_set)) {
                    val currencySetIds = remember(currencySets) { SelectListKeys + currencySets.keys }
                    val currencySetNames = remember(currencySets, selectListValues) {
                        selectListValues + currencySets.values.map { it.label }
                    }
                    val expanded = remember { mutableStateOf(false) }
                    JetPrefDropdown(
                        options = currencySetNames,
                        expanded = expanded,
                        selectedOptionIndex = currencySetIds.indexOf(currencySet).coerceAtLeast(0),
                        isError = showSelectAsError && currencySet == SelectComponentName,
                        onSelectOption = { currencySet = currencySetIds[it] },
                        appearance = OwnkeyTextFieldDefaults.jetPrefAppearance(),
                    )
                }
                LayoutField(stringRes(R.string.settings__localization__subtype_numeric_layout), LayoutType.NUMERIC)
                LayoutField(stringRes(R.string.settings__localization__subtype_numeric_advanced_layout), LayoutType.NUMERIC_ADVANCED)
                LayoutField(stringRes(R.string.settings__localization__subtype_numeric_row_layout), LayoutType.NUMERIC_ROW)
                LayoutField(stringRes(R.string.settings__localization__subtype_phone_layout), LayoutType.PHONE)
                LayoutField(stringRes(R.string.settings__localization__subtype_phone2_layout), LayoutType.PHONE2)
            }
        }
    }

    if (showDeleteDialog && id != null) {
        DeleteSubtypeConfirmationDialog(
            subtypeToDelete = subtypeManager.getSubtypeById(id),
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                subtypeManager.getSubtypeById(id)?.let { subtypeManager.removeSubtype(it) }
                navController.popBackStack()
            },
        )
    }
}

/** A labelled field: label above in the group-header style, then the field. */
@Composable
private fun SubtypeProperty(text: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = OwnkeyTokens.Secondary,
        )
        content()
    }
}

@Composable
private fun SubtypeLayoutDropdown(
    layoutType: LayoutType,
    layouts: Map<ExtensionComponentName, LayoutArrangementComponent>,
    showSelectAsError: Boolean,
    layoutMap: SubtypeLayoutMap,
    onLayoutMapChanged: (SubtypeLayoutMap) -> Unit,
    selectListValues: List<String>,
) {
    val layoutIds = remember(layouts) { SelectListKeys + layouts.keys.toList() }
    val layoutLabels = remember(layouts, selectListValues) { selectListValues + layouts.values.map { it.label } }
    val layoutId = remember(layoutMap) { layoutMap[layoutType] }
    val expanded = remember { mutableStateOf(false) }
    val selectedIndex = layoutIds.indexOf(layoutId).coerceAtLeast(0)
    JetPrefDropdown(
        options = layoutLabels,
        expanded = expanded,
        selectedOptionIndex = selectedIndex,
        isError = showSelectAsError && selectedIndex == 0,
        onSelectOption = { onLayoutMapChanged(layoutMap.copy(layoutType = layoutType, componentName = layoutIds[it])!!) },
        appearance = OwnkeyTextFieldDefaults.jetPrefAppearance(),
    )
}
