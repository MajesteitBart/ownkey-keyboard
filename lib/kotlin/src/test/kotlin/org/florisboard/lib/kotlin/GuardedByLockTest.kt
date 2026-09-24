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

package org.florisboard.lib.kotlin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class GuardedByLockTest : FunSpec({
    test("tryWithLock runs the action when the lock is free") {
        val guarded = guardedByLock { mutableListOf(1, 2) }
        guarded.tryWithLock { it.size } shouldBe 2
    }

    test("tryWithLock returns null instead of waiting while the lock is held") {
        val guarded = guardedByLock { mutableListOf(1) }
        guarded.withLock {
            guarded.tryWithLock { it.size }.shouldBeNull()
        }
        guarded.tryWithLock { it.size } shouldBe 1
    }
})
