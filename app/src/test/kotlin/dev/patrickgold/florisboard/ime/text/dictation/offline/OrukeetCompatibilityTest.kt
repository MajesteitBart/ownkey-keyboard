package dev.patrickgold.florisboard.ime.text.dictation.offline

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OrukeetCompatibilityTest : FunSpec({
    test("32-bit installations cannot download a runtime even on dual-ABI devices") {
        supportsOrukeetRuntime(debug = true, process64Bit = false, supportedAbis = listOf("arm64-v8a", "armeabi-v7a")) shouldBe false
        supportsOrukeetRuntime(debug = false, process64Bit = false, supportedAbis = listOf("arm64-v8a", "armeabi-v7a")) shouldBe false
        supportsOrukeetRuntime(debug = true, process64Bit = false, supportedAbis = listOf("x86_64", "x86")) shouldBe false
    }
    test("arm64 installs are supported in public and internal builds") {
        supportsOrukeetRuntime(debug = false, process64Bit = true, supportedAbis = listOf("arm64-v8a")) shouldBe true
        supportsOrukeetRuntime(debug = false, process64Bit = true, supportedAbis = listOf("arm64-v8a", "armeabi-v7a")) shouldBe true
        supportsOrukeetRuntime(debug = true, process64Bit = true, supportedAbis = listOf("arm64-v8a")) shouldBe true
    }
    test("x86_64 is supported only in debug emulator builds") {
        supportsOrukeetRuntime(debug = true, process64Bit = true, supportedAbis = listOf("x86_64", "x86")) shouldBe true
        supportsOrukeetRuntime(debug = false, process64Bit = true, supportedAbis = listOf("x86_64")) shouldBe false
    }
    test("other runtimes stay unavailable") {
        supportsOrukeetRuntime(debug = true, process64Bit = true, supportedAbis = listOf("riscv64")) shouldBe false
    }
})
