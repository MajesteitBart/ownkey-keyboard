package dev.patrickgold.florisboard.ime.text.dictation.offline

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OrukeetCompatibilityTest : FunSpec({
    test("32-bit installations cannot download a runtime even on dual-ABI devices") {
        supportsOrukeetRuntime(true, true, false, listOf("arm64-v8a", "armeabi-v7a")) shouldBe false
        supportsOrukeetRuntime(true, true, false, listOf("x86_64", "x86")) shouldBe false
    }
    test("internal arm64 and debug emulator installs remain supported") {
        supportsOrukeetRuntime(true, false, true, listOf("arm64-v8a", "armeabi-v7a")) shouldBe true
        supportsOrukeetRuntime(true, true, true, listOf("x86_64", "x86")) shouldBe true
    }
    test("public builds and unsupported runtimes stay unavailable") {
        supportsOrukeetRuntime(false, false, true, listOf("arm64-v8a")) shouldBe false
        supportsOrukeetRuntime(true, false, true, listOf("x86_64")) shouldBe false
        supportsOrukeetRuntime(true, true, true, listOf("riscv64")) shouldBe false
    }
})
