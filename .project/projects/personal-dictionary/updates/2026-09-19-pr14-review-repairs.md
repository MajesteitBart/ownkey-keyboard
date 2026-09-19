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

- Follow-up sentence repair recognizes terminal punctuation before closing quotes/brackets. The extended regression fails before the change and covers straight/curly quotes, a quote without terminal punctuation, and intentional name casing. [Review reference](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053225155).
- Opening quotes now establish retained leading boundaries, with a preceding-word guard to protect names containing apostrophes. Quote regression coverage includes straight, curly and single quotes at a filler, plus intentional casing and apostrophes inside names. [Review reference](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053252221).
- The preceding-word guard applies only to apostrophe delimiters; other quotes/brackets work immediately after words. Mid-sentence parentheticals retain lowercase text while enclosed sentence starts receive capitalization. Tests cover unspaced CJK quotation, inline parentheses, a sentence enclosed in parentheses, and consecutive fillers. [Parenthetical casing](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053278289), [delimiter guard](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053278294).

## Interrupted Unreadable Recovery

- A missing main file with a kept unreadable copy now reports `UNREADABLE` until a valid main is saved or recovery is persisted. Existing valid backup recovery clears the warning; the damaged copy remains preserved.
- Added restart and successful-backup regressions. The interrupted-load regression failed before the repair.

Reference: [interrupted load warning](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053207427).

## Persistence Feedback and Native Diagnostics

- Entry save results now carry the persistence outcome captured while holding the mutation lock. Settings forms retain failed drafts and retry the existing entry ID; edit dialogs stay open with a storage error. Save controls prevent overlapping submissions. The keyboard also uses the returned outcome rather than a later global state snapshot.
- Native exceptions produce only fixed diagnostic codes, without throwable messages, causes, stack traces or native paths. A pure unit test covers message-bearing runtime, linkage and memory failures.
- Disk-backed storage tests cover failed word/correction adds and edits, successful retry without duplicates, and immutable per-operation results after a later successful save. No Android device was connected for a new UI smoke test.

References: [native diagnostics](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053294977), [save feedback](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053294980).

- Edit-dialog dismissal is disabled during an accepted save. Retried add drafts identify their original word/source rather than retaining a failed write's numeric ID across process death, where that ID could belong to a different entry.
- Failure to persist a consumed-file marker now keeps recovery and later edits marked unsaved. The marker is flushed and replaced atomically where supported; failures propagate through load and mutation outcomes. A disk-backed regression covers marker path collision, failed deletion, a removed recovered word, successful retry and restart without resurrection.

References: [dialog dismissal](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053312489), [marker persistence](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053319334).

## Atomic Recovery Record

- Consumed quarantine filenames now travel inside the same atomically replaced dictionary document as its entries. A crash after main replacement and before retirement can no longer remerge a file whose recovered entries the user deleted.
- Portable exports strip this local metadata. Readers still understand older sidecar markers, but new main writes do not depend on a second file write for durability. This supersedes the earlier rule that a failed sidecar must report an unsaved main: the main now contains its own committed skip state, so successful persistence is truthful even if optional compatibility-marker writing fails.
- Fallback backup recovery honors its embedded record only when it accounts for all kept files, preserving the earlier rule that an unmarked stale backup must not shadow a newer kept main.
- Real-file tests snapshot the exact post-replacement/pre-retirement crash window, restart without a sidecar, simulate a failed sidecar path, check portable export, and recover a fallback checkpoint without resurrecting removed entries. Both newly identified crash paths failed before their repairs.

Reference: [crash window between main and marker](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053366395).

## Fallback Checkpoints and Backup Preflight

- Rewriting an unreadable main from its fallback now preserves the fallback's consumed-file record. A real-file regression failed before the repair and verifies the rewritten checkpoint plus a second restart without resurrecting deleted entries.
- The crash-window test separately asserts the restarted document contains its recovery record and the portable export omits it.
- Selected speech backup data is decoded and validated before preferences, extensions or clipboard are changed. A regression verifies preflight rejects incompatible and invalid rows without changing live state or disk. Restore persistence results are captured under the mutation lock.
- Speech backup write failures become a fixed localized error without the original exception cause or private paths. Coroutine cancellation still propagates.
- All 463 app tests, debug assembly and release Kotlin compilation pass. No new device smoke test was possible; physical Orukeet hotword qualification remains pending.

References: [fallback record](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053404281), [restart assertion](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053402420), [restore preflight](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053404283), [backup logging](https://github.com/MajesteitBart/ownkey-keyboard/pull/14#discussion_r4053404285).
