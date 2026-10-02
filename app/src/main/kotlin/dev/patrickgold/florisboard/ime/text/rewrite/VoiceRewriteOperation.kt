/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.rewrite

object VoiceRewritePolicy {
    const val FixedInstruction =
        "Rewrite the user-provided source text according to the separate user edit instruction. " +
            "Return only the rewritten text. Keep the result in the source text's language unless the user " +
            "explicitly requests another language. Preserve mixed-language source text rather than normalizing it."
}

/**
 * Not a `fun interface` on purpose. Kotlin 2.3 compiled the SAM-converted suspend lambda for this
 * interface so that it returned the inline-class [Result] boxed, while callers expect it unboxed when
 * the call finishes without suspending. The session manager then got a `Result` inside a `Result` and
 * crashed with a ClassCastException. Implement this interface with a class or object.
 */
interface VoiceRewriteOperation {
    suspend fun rewrite(sourceText: String, instruction: String): Result<String>
}

internal object UnconfiguredVoiceRewriteOperation : VoiceRewriteOperation {
    override suspend fun rewrite(sourceText: String, instruction: String): Result<String> =
        Result.failure(IllegalStateException("Voice rewrite operation is not configured"))
}

class LlmVoiceRewriteOperation(
    private val client: LlmRewriteClient,
) : VoiceRewriteOperation {
    override suspend fun rewrite(sourceText: String, instruction: String): Result<String> =
        client.rewriteWithVoiceInstruction(sourceText, instruction)
}
