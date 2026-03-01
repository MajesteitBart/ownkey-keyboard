---
timestamp: 2026-02-25T21:28:00Z
status: review
task: T-006
stream: ws-3-personalization-and-multilingual-adaptation
---

# Progress Update

## Completed
- Added `MixedLanguageScoringPolicy` to balance language confidence from subtype priors, recent context evidence, and exact-input language matches.
- Updated `LatinLanguageProvider` suggestion ranking to aggregate prefix + typo candidates across primary/secondary locale models and rank with language-weighted scoring and confidence blending.
- Extended next-word fallback and context scoring to use weighted multilingual language contexts instead of primary-language-only ranking.
- Updated spelling and user-dictionary checks to evaluate all subtype locales so mixed-language input no longer depends on manual language switching.
- Added locale resolution for accepted/reverted suggestions so rapid personal vocabulary learning promotes entries into the matching language locale dictionary.
- Added `MixedLanguageScoringPolicyTest` unit coverage for language-prior defaulting, mixed-context shifts, exact-match boosts, and weighted ranking/confidence behavior.

## Verification
- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.nlp.latin.*"` (pass)
- `./gradlew :app:testDebugUnitTest` (pass)

## Privacy Notes
- Mixed-language balancing uses only on-device dictionaries and in-memory context windows.
- No raw typed text is persisted or emitted as telemetry.
- Learning promotion remains local-only and updates locale-scoped user dictionary entries.

## Next Actions
- Move T-006 to done after review sign-off.
