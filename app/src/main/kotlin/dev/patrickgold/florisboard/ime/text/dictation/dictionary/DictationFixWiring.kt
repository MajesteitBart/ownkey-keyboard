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

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorInstance
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralDictationManager
import dev.patrickgold.florisboard.ime.text.rewrite.AiAvailability
import dev.patrickgold.florisboard.ime.text.rewrite.AiAvailabilityPolicy
import dev.patrickgold.florisboard.lib.util.launchActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Ordinary dictation lifecycle as seen by the keyboard-side fix flow. */
interface DictationInsertionListener {
    fun onDictationStarted()
    fun onDictationInserted(insertion: DictationInsertion)
}

class EditorInstanceDictationFixGateway(private val editorInstance: EditorInstance) : DictationFixEditorGateway {
    override val contentFlow: Flow<EditorContent> get() = editorInstance.activeContentFlow
    override val activeSessionId: Long get() = editorInstance.activeInputSessionId
    override val activeHostPackage: String? get() = editorInstance.activeInfo.packageName
    override val activeFieldId: Int get() = editorInstance.activeInfo.base.fieldId
    override fun selectRange(start: Int, end: Int): Boolean = editorInstance.setSelection(start, end)
}

const val SPEECH_DICTIONARY_DEEPLINK = "ui://florisboard/settings/voxtral/dictionary"
const val SPEECH_DICTIONARY_HEARD_PARAM = "heard"

fun createDictationFixController(
    context: Context,
    scope: CoroutineScope,
    repository: SpeechDictionaryRepository,
    editorInstance: EditorInstance,
    dictationManager: VoxtralDictationManager,
    availabilityPolicy: AiAvailabilityPolicy,
): DictationFixController {
    val appContext = context.applicationContext
    val controller = DictationFixController(
        scope = scope,
        repository = repository,
        editor = EditorInstanceDictationFixGateway(editorInstance),
        openDictionary = { heard -> appContext.openSpeechDictionarySettings(heard) },
        // Incognito and secure fields end the flow wherever it is; no correction may be learned then.
        available = availabilityPolicy.state.map { it is AiAvailability.Available },
    )
    dictationManager.insertionListener = object : DictationInsertionListener {
        override fun onDictationStarted() = controller.interrupt()
        override fun onDictationInserted(insertion: DictationInsertion) = controller.offer(insertion)
    }
    return controller
}

private fun Context.openSpeechDictionarySettings(heard: String) {
    FlorisImeService.hideUi()
    launchActivity(FlorisAppActivity::class) { intent ->
        // The settings host only treats ACTION_VIEW as a navigation deep link when the browsable
        // category is present; without it the same URI is routed to extension import instead.
        intent.action = Intent.ACTION_VIEW
        intent.data = SPEECH_DICTIONARY_DEEPLINK.toUri().buildUpon()
            .apply { if (heard.isNotBlank()) appendQueryParameter(SPEECH_DICTIONARY_HEARD_PARAM, heard) }
            .build()
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
}
