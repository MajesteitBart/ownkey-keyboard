package dev.patrickgold.florisboard.ime.text.dictation.offline

/**
 * Runtime libraries ship only in 64-bit APK splits; device ABI alone is insufficient.
 * Internal arm64 builds are supported. x86_64 is reserved for debug internal emulator builds.
 * All 32-bit processes and public builds remain unsupported.
 */
internal fun supportsOrukeetRuntime(
    internalBuild: Boolean,
    debug: Boolean,
    process64Bit: Boolean,
    supportedAbis: List<String>,
): Boolean = internalBuild && process64Bit &&
    ("arm64-v8a" in supportedAbis || (debug && "x86_64" in supportedAbis))
