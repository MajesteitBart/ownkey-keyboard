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

## Settings Durability and Sentence Boundaries

- Settings mutations now finish independently of navigation cancellation. Only keyboard fix saves opt into cancellation before committing; repeated Save taps cancel the prior pending save so an incognito transition cannot leave an older save running.
- Localized sentence marks delimit fillers even without spaces before the next sentence. Spacing repair uses following punctuation only, preserving opening quotes and inverted question/exclamation marks. Regression coverage also preserves domain names and intentional casing.
- Regressions failed before repairs for settings navigation, unspaced sentence boundaries, opening punctuation, and repeated-save cancellation. Final verification passes 453 app tests with zero failures/errors/skips, debug APK assembly, and release Kotlin compilation.
- A real-file cancellation test for a retried quarantine confirms that the newer document survives byte-for-byte in its kept file, an older restart does not resurrect cancelled data, and an upgraded reader recovers the original vocabulary. This refutes the claimed data loss; forcing the cancelled keyboard entry onto disk would undermine the incognito guarantee.
- Android device qualification remains pending; no connected device was available. Fresh final-head review and CI remain required before merging into the Orukeet branch.

References: [settings navigation](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053130336), [unspaced sentences](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053130341), [quarantine cancellation](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053141896), [opening punctuation](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053141904).

## Endpoint Parsing and Combined Saves

- Automatic cloud vocabulary disclosure now derives the host through the same `java.net.URL` parser as the HTTP client and accepts only HTTP(S). Fragment/user-info lookalikes, malformed ports, missing schemes, and unrelated hosts receive no automatic hints.
- Keyboard correction and optional vocabulary learning now share one repository mutation and file replacement. Dismissal during persistence cannot leave half a requested save; incognito cancellation before replacement discards both entries. Existing vocabulary is reused, with unique IDs preserved when updating a correction.
- Before these repairs, two added regressions failed: endpoint impersonation through a fragment and dismissal during persistence. Fresh final-head review and CI remain required.

References: [endpoint parsing](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053181004), [atomic learning](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053181008).

## Quote Boundaries

- Closing quotes establish a filler boundary without being consumed; apostrophes inside words remain protected. Recapitalization skips opening punctuation while preserving intentional casing.
- Extended the quote regression with a lowercase quoted sentence, an inverted question mark, a filler before a closing quote, and a possessive spelling. The extended regression failed before the repair; all 456 tests, debug assembly and release Kotlin compilation pass afterward.

References: [closing quotes](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053190858), [capitalization after opening marks](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053190869).

## Interrupted Unreadable Recovery

- A missing main file with a kept unreadable copy now reports `UNREADABLE` until a valid main is saved or recovery is persisted. Existing valid backup recovery clears the warning; the damaged copy remains preserved.
- Added restart and successful-backup regressions. The interrupted-load regression failed before the repair.

Reference: [interrupted load warning](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053207427).

- Follow-up sentence repair recognizes terminal punctuation before closing quotes/brackets. The extended regression fails before the change and covers straight/curly quotes, a quote without terminal punctuation, and intentional name casing. [Review reference](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053225155).
