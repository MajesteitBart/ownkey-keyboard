package dev.patrickgold.florisboard.ime.text.dictation.offline

/**
 * Runtime libraries ship only in 64-bit APK splits; device ABI alone is insufficient.
 * arm64 installs are supported in every build. x86_64 is reserved for debug emulator builds.
 * All 32-bit processes remain unsupported.
 */
internal fun supportsOrukeetRuntime(
    debug: Boolean,
    process64Bit: Boolean,
    supportedAbis: List<String>,
): Boolean = process64Bit &&
    ("arm64-v8a" in supportedAbis || (debug && "x86_64" in supportedAbis))
