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

package org.florisboard.lib.android

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class OwnkeyToastMessage(
    val id: Long,
    val text: String,
    val durationMillis: Long,
)

object OwnkeyToastBus {
    private val activeImeHosts = AtomicInteger(0)
    private val messageIds = AtomicLong(0L)
    private val _messages = MutableSharedFlow<OwnkeyToastMessage>(
        extraBufferCapacity = 8,
    )

    val messages: SharedFlow<OwnkeyToastMessage> = _messages

    fun registerImeHost(): Registration {
        activeImeHosts.incrementAndGet()
        return Registration()
    }

    fun tryShowInIme(text: String, durationMillis: Long): Boolean {
        if (activeImeHosts.get() <= 0) return false
        return _messages.tryEmit(
            OwnkeyToastMessage(
                id = messageIds.incrementAndGet(),
                text = text,
                durationMillis = durationMillis,
            )
        )
    }

    class Registration internal constructor() {
        private val closed = AtomicInteger(0)

        fun close() {
            if (closed.getAndSet(1) == 0) {
                activeImeHosts.decrementAndGet()
            }
        }
    }
}
