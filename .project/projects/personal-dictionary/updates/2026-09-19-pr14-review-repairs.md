---
timestamp: 2026-09-19T11:44:00Z
status: in-progress
task: T-001
stream: WS-A
---

# PR #14 Review Repairs

## Changes

- Retire the stale dictionary backup before moving a newer main document aside. Failed deletion keeps the authoritative main file untouched and blocks writes until the operation can retry safely.
- Missing-main recovery ignores a stale backup whenever a newer-version main document was kept, including after an upgrade now supports that document. This handles an interruption between rename and backup deletion in older builds without restoring deleted words or outdated corrections.
- Report failed recovery/merge writes through `saveError`, preserving the existing retry behavior and kept files.
- Treat Arabic, Indic, Armenian, Syriac, Ethiopic, and reversed-question sentence marks left after filler removal as punctuation. Meaningful symbols and intentionally dictated punctuation remain insertable.

## Evidence

- Before the repair, the full app suite ran 447 tests with exactly three new regression failures: non-Latin punctuation, stale-backup recovery after upgrade, and failure to preserve the main file when deleting the stale backup fails.
- After the repair, `:app:testDebugUnitTest` passes 447 tests with zero failures/errors/skips. Tests exercise real dictionary files, simulated failed deletion, recovery across two restarts, current correction precedence, and the ordinary dictation cleanup result.
- `:app:assembleDebug` and `:app:compileReleaseKotlin` passed in the same verification run. Final remote CI/review are pending at the time of this entry; the PR handoff will record their outcomes.
- Gradle's `--tests` filtering did not discover these Kotest specs, so verification uses the complete app suite rather than claiming a filtered run passed.
- No Android emulator or physical device was connected. Physical Orukeet hotword qualification remains T-005; local hints stay off by default.

## Review References

- [Codex: stale-backup quarantine](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4046050822).
- [Cubic: non-Latin sentence punctuation](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4046013147).
- [Earlier recovery warning finding](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4044062720).

## Remaining Gates

Final-head Codex review and Android CI. PR #14 targets the Orukeet feature branch; merging it does not merge PR #13 into main or enable public local inference.

## Follow-up Review

- The first repaired head passed Android CI, but its completed Codex review identified directly attached localized punctuation and a cancellation window while a save waited for IO.
- Filler matching, sentence-boundary repair, and content detection now share the punctuation inventory. Tests cover both spaced and directly attached marks, sentence boundaries, and intentional name casing.
- Saves retain the caller's cancellation context through the non-cancellable transaction. Cancellation is checked upon entering IO and immediately before replacing the main file (including the non-atomic fallback). A cancelled draft is discarded, not retained for later retry. Once replacement starts, publication finishes so disk and memory agree.
- The incognito test now drives the availability signal while IO is queued and verifies neither entry reaches memory or disk. A real-file test cancels after IO starts but before replacement, verifies the prior file/state and temporary-file cleanup, and checks a later successful save cannot resurrect the cancelled word.
- The interrupted-quarantine regression explicitly verifies the newly persisted main document, remaining stale backup, retired kept file, and a restart from that persisted document.
- Follow-up verification: 449 app tests pass with zero failures/errors/skips; debug APK assembly and release Kotlin compilation pass. The next head still requires fresh remote review and CI.

References: [attached punctuation](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053105894), [incognito cancellation](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053105897), [explicit recovery assertions](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053105254).
