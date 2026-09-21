package dev.patrickgold.florisboard.ime.text.dictation.offline

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OrukeetCompatibilityTest : FunSpec({
    test("32-bit installations cannot download a runtime even on dual-ABI devices") {
        supportsOrukeetRuntime(internalBuild = true, debug = true, process64Bit = false, supportedAbis = listOf("arm64-v8a", "armeabi-v7a")) shouldBe false
        supportsOrukeetRuntime(internalBuild = true, debug = true, process64Bit = false, supportedAbis = listOf("x86_64", "x86")) shouldBe false
    }
    test("internal arm64 and debug emulator installs remain supported") {
        supportsOrukeetRuntime(internalBuild = true, debug = false, process64Bit = true, supportedAbis = listOf("arm64-v8a", "armeabi-v7a")) shouldBe true
        supportsOrukeetRuntime(internalBuild = true, debug = true, process64Bit = true, supportedAbis = listOf("x86_64", "x86")) shouldBe true
    }
    test("public builds and unsupported runtimes stay unavailable") {
        supportsOrukeetRuntime(internalBuild = false, debug = false, process64Bit = true, supportedAbis = listOf("arm64-v8a")) shouldBe false
        supportsOrukeetRuntime(internalBuild = true, debug = false, process64Bit = true, supportedAbis = listOf("x86_64")) shouldBe false
        supportsOrukeetRuntime(internalBuild = true, debug = true, process64Bit = true, supportedAbis = listOf("riscv64")) shouldBe false
    }
})
