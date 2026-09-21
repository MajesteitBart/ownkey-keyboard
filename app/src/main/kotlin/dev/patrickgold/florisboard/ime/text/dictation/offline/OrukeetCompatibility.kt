package dev.patrickgold.florisboard.ime.text.dictation.offline

/** Runtime libraries ship only in the 64-bit APK splits. Device ABI support alone is insufficient. */
internal fun supportsOrukeetRuntime(
    internalBuild: Boolean,
    debug: Boolean,
    process64Bit: Boolean,
    supportedAbis: List<String>,
): Boolean = internalBuild && process64Bit &&
    ("arm64-v8a" in supportedAbis || (debug && "x86_64" in supportedAbis))
