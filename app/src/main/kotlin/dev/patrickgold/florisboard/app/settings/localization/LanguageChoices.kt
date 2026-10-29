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

package dev.patrickgold.florisboard.app.settings.localization

import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.core.SubtypeNlpProviderMap
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName

/** The two lists of the Add language screen. */
data class PresetSections<T>(
    /** Presets for the phone's own languages, in the phone's order. */
    val suggested: List<T>,
    /** Every other preset, sorted by name. */
    val others: List<T>,
)

/**
 * Splits [presets] into the phone's languages and the rest, filtered by [query].
 *
 * A phone language suggests the presets with exactly its locale; when there is none, the presets that share
 * its language. A preset matches [query] when one of its [namesOf] contains it or its language tag starts
 * with it, ignoring case.
 */
fun <T> sectionPresets(
    presets: List<T>,
    localeOf: (T) -> FlorisLocale,
    namesOf: (T) -> List<String>,
    phoneLocales: List<FlorisLocale>,
    query: String,
): PresetSections<T> {
    val term = query.trim().lowercase()
    fun matches(preset: T): Boolean {
        if (term.isEmpty()) return true
        return namesOf(preset).any { it.lowercase().contains(term) } ||
            localeOf(preset).languageTag().lowercase().startsWith(term)
    }

    val suggested = LinkedHashSet<T>()
    for (phoneLocale in phoneLocales) {
        val exact = presets.filter { localeOf(it) == phoneLocale }
        val forLocale = exact.ifEmpty { presets.filter { localeOf(it).language == phoneLocale.language } }
        suggested.addAll(forLocale)
    }
    val others = presets
        .filter { it !in suggested }
        .sortedBy { namesOf(it).firstOrNull()?.lowercase().orEmpty() }
    return PresetSections(
        suggested = suggested.filter(::matches),
        others = others.filter(::matches),
    )
}

/**
 * Fills every field of [current] that still holds [unset] from [fallback]. The subtype editor calls this
 * when the language changes, with the preset for the new language or [Subtype.DEFAULT].
 */
fun fillUnsetSubtypeFields(
    current: Subtype,
    fallback: Subtype,
    unset: ExtensionComponentName,
): Subtype {
    val unsetProvider = unset.toString()
    val nlpProviders = if (current.nlpProviders.spelling == unsetProvider ||
        current.nlpProviders.suggestion == unsetProvider
    ) {
        SubtypeNlpProviderMap(
            spelling = current.nlpProviders.spelling.takeIf { it != unsetProvider } ?: fallback.nlpProviders.spelling,
            suggestion = current.nlpProviders.suggestion.takeIf { it != unsetProvider } ?: fallback.nlpProviders.suggestion,
        )
    } else {
        current.nlpProviders
    }
    var layoutMap = current.layoutMap
    for (layoutType in EditableLayoutTypes) {
        if (layoutMap[layoutType] == unset) {
            val fallbackLayout = fallback.layoutMap[layoutType] ?: continue
            layoutMap = layoutMap.copy(layoutType, fallbackLayout) ?: layoutMap
        }
    }
    return current.copy(
        nlpProviders = nlpProviders,
        composer = current.composer.takeIf { it != unset } ?: fallback.composer,
        currencySet = current.currencySet.takeIf { it != unset } ?: fallback.currencySet,
        punctuationRule = current.punctuationRule.takeIf { it != unset } ?: fallback.punctuationRule,
        popupMapping = current.popupMapping.takeIf { it != unset } ?: fallback.popupMapping,
        layoutMap = layoutMap,
    )
}

/** The layout slots a subtype stores, in the order the editor shows them. */
val EditableLayoutTypes = listOf(
    LayoutType.CHARACTERS,
    LayoutType.SYMBOLS,
    LayoutType.SYMBOLS2,
    LayoutType.NUMERIC,
    LayoutType.NUMERIC_ADVANCED,
    LayoutType.NUMERIC_ROW,
    LayoutType.PHONE,
    LayoutType.PHONE2,
)
