---
timestamp: 2026-02-25T21:05:59Z
status: review
task: T-005
stream: ws-3-personalization-and-multilingual-adaptation
---

# Progress Update

## Completed
- Added `RapidPersonalVocabularyLearner` to track in-memory word confirmations with 1-3 promotion thresholds based on confidence and per-language partitioning.
- Added quality safeguards for rapid learning: strict token filtering, exposure decay over time, and temporary suppression when users revert a suggestion.
- Integrated learner callbacks into `LatinLanguageProvider` suggestion acceptance/revert notifications.
- Implemented promotion upsert into the Floris user dictionary with locale-scoped entries to remain compatible with dictionary partitioning and existing lookup behavior.
- Cleared suggestion cache after promotion so newly learned terms can surface immediately.
- Added `RapidPersonalVocabularyLearnerTest` unit coverage for promotion thresholds, decay, suppression, language partitioning, and noisy token rejection.

## Verification
- `./gradlew :app:compileDebugKotlin` (pass)
- `./gradlew :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.nlp.latin.RapidPersonalVocabularyLearnerTest"` (pass)
- `./gradlew :app:testDebugUnitTest` (pass)

## Privacy Notes
- Exposure tracking is ephemeral and in-memory only.
- Learning logic avoids telemetry/logging of raw candidate terms.
- Promotion persists only filtered terms in the local on-device user dictionary.

## Next Actions
- Move T-005 to done after review sign-off.
