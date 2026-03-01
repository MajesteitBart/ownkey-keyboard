---
timestamp: 2026-03-01T20:50:00Z
status: review
task: T-007
stream: ws-4-context-policy-and-metrics
---

# Progress Update

## Completed
- Added `AppSpecificAutocorrectProfilePolicy` to map major app packages to context profiles (`CHAT`, `EMAIL`, `DEFAULT`) and keep a safe default fallback for unknown contexts.
- Added heuristic fallback classification for unknown packages using input variation (`EMAIL_*`, `SHORT_MESSAGE`) and IME action (`SEND`) so profile resolution still works when app package mapping is absent.
- Integrated profile-aware scaling into `LatinLanguageProvider` high-certainty autocorrect config generation, making auto-commit thresholds dynamically stricter in email contexts and more permissive in chat contexts.
- Extended suggestion cache keys with an autocorrect policy signature so context/profile changes do not reuse stale cached auto-commit eligibility.
- Added correction preferences for profile toggle and aggressiveness tuning (`chat`, `email`) plus Typing settings sliders to make behavior configurable in-app.
- Added unit tests in `AppSpecificAutocorrectProfilePolicyTest` for package mapping, heuristic fallback, safe default behavior, and aggressiveness scaling.

## Verification
- `JAVA_HOME=/home/bartadmin/.local/jdks/temurin-17 ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.nlp.latin.*"` (pass)

## Privacy Notes
- App-context handling is based on editor metadata (`packageName`, input variation, IME action) already provided by the Android IME framework.
- No raw typed text is persisted or exported.
- Profile tuning is local preference state only.

## Next Actions
- Move T-007 to done after review sign-off.
