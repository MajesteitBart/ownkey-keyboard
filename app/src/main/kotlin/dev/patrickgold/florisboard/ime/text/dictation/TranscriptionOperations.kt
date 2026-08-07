/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class TranscriptionFailureReason {
    RECORDING,
    PROVIDER,
}

sealed interface TranscriptionOutcome {
    data class Transcript(val text: String) : TranscriptionOutcome
    data class Failure(val reason: TranscriptionFailureReason) : TranscriptionOutcome
    data object Empty : TranscriptionOutcome
    data object Cancelled : TranscriptionOutcome
}

/** Returns a transcript without knowing about, or mutating, an editor. */
class TranscriptionOnlyOperation(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun stopAndTranscribe(
        lease: AudioSessionLease,
        client: TranscriptionClient,
    ): TranscriptionOutcome {
        return try {
            currentCoroutineContext().ensureActive()
            val recording = when (val stopResult = lease.stop()) {
                is AudioSessionStopResult.Stopped -> stopResult.recording
                is AudioSessionStopResult.Failed -> {
                    return if (stopResult.error is CancellationException) {
                        lease.cancel()
                        TranscriptionOutcome.Cancelled
                    } else {
                        TranscriptionOutcome.Failure(TranscriptionFailureReason.RECORDING)
                    }
                }
                AudioSessionStopResult.AlreadyStopped,
                AudioSessionStopResult.Stale -> return TranscriptionOutcome.Cancelled
            }
            currentCoroutineContext().ensureActive()
            val result = withContext(ioDispatcher) { client.transcribe(recording) }
            result.fold(
                onSuccess = { transcript ->
                    transcript.trim().takeIf { it.isNotEmpty() }
                        ?.let(TranscriptionOutcome::Transcript)
                        ?: TranscriptionOutcome.Empty
                },
                onFailure = { error ->
                    if (error is CancellationException) {
                        lease.cancel()
                        TranscriptionOutcome.Cancelled
                    } else {
                        TranscriptionOutcome.Failure(TranscriptionFailureReason.PROVIDER)
                    }
                },
            )
        } catch (_: CancellationException) {
            lease.cancel()
            TranscriptionOutcome.Cancelled
        }
    }
}

sealed interface OrdinaryDictationCommitResult {
    data object Committed : OrdinaryDictationCommitResult
    data object CommitFailed : OrdinaryDictationCommitResult
    data class NotCommitted(val outcome: TranscriptionOutcome) : OrdinaryDictationCommitResult
}

/** Keeps the ordinary dictation editor mutation explicit and outside transcription. */
class OrdinaryDictationCommitOperation(
    private val commitText: (String) -> Boolean,
) {
    fun commit(outcome: TranscriptionOutcome): OrdinaryDictationCommitResult = when (outcome) {
        is TranscriptionOutcome.Transcript -> {
            if (commitText(outcome.text)) {
                OrdinaryDictationCommitResult.Committed
            } else {
                OrdinaryDictationCommitResult.CommitFailed
            }
        }
        else -> OrdinaryDictationCommitResult.NotCommitted(outcome)
    }
}
