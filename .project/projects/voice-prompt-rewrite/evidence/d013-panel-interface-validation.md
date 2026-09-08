# D-013 panel interface validation

Evidence for the dictation and rewrite interface change (D-013): voice rewrite renders as one
state-driven body inside the AI rewrite panel, the smartbar recording row is dictation-only, and
dictation inserts its own word boundary.

## Automated checks (2026-09-08)

| Command | Result |
| --- | --- |
| `gradlew.bat :app:testDebugUnitTest --console=plain` | 312 passed, 0 failed, 0 skipped |
| `gradlew.bat :app:compileDebugKotlin` | success |
| `gradlew.bat :app:compileReleaseKotlin` | success |
| `git diff --check` | clean |
| `delano validate` | 0 errors, 0 warnings |

Specs added or extended in this pass:

- `RewritePanelBodyTest`: every voice surface maps to one body, recording and paused share a body, the preset flow only drives the body while no voice session is active.
- `VoiceRewriteUiModelTest`: `isCapturing` is true only for recording and paused; a reviewable result offers Close, Try again, Record again, and one Replace; every surface exposes an exit.
- `VoiceRewriteUiControllerTest`: a confirmation-bound finish is ignored once the surface it was started for is gone.
- `VoiceRecordingRowModelTest`: a voice-rewrite session never produces a smartbar row.
- `VoiceRewriteRecordingClockTest`: elapsed time excludes pauses; the countdown appears only in the last five seconds and never during processing.
- `RewritePresetHubRowsTest`: the six defaults split into a quality/length row and a tone/language row; custom presets wrap into further rows.
- `DictationInsertionSpacingTest`: insertion after sentence punctuation and after a word, existing spaces and newlines, empty field, replacement of a selection, beginning and middle of text, leading punctuation in the transcript, opening and closing quotes and brackets, and scripts without inter-word spaces.

## Not covered here

No emulator or physical-device session was run in this pass. The rendered state matrix (hub to
recording to processing to result to success, pause/resume, long instruction and result, cancel at
each stage, recovery variants, rapid close/reopen after success), Samsung Notes and other editors,
dark/light themes, large font scale, narrow portrait, landscape and tablet/split layouts, and
TalkBack focus and announcements remain pending and should be exercised before release.

## Review follow-ups (2026-09-08)

An independent code review of the diff was applied before merge: a straight quote after the
cursor no longer receives a space before it, the panel waveform can no longer push the action rail
out of the sheet, the smartbar stops ticking during a voice-rewrite recording, header controls are
48 dp, rail labels may wrap to two lines, every voice body keeps a label and an exit even without a
model message, and the accent ink switches to dark on light user accents. The full unit suite was
rerun afterwards. Candidate staleness after replacement was not reproduced in this
pass and remains hidden-only while the panel is open.

## Second review round (2026-09-08)

Pull-request review follow-ups applied and re-verified with the full unit suite (312 passed):
the smartbar close control cancels an active voice-rewrite session and releases the recorder
explicitly (controller test asserts the audio lease is gone); the confirmation-bound finish reads
the session manager's state directly so a stale timer cannot reset a flow that started before the
derived UI state caught up; every Unicode space separator counts as existing whitespace for
dictation insertion; the hub scope summary refreshes on any host content change while the hub is
shown and holds no editor subscription in other states; the action rail grows to fit two-line
labels at large font scales; the spec's recording placement sections (executive summary, AC-028
and new AC-028a, recording-instruction section, compact layout, in-scope list, approval notes)
now match D-013, and the in-panel confirmation dwell is 1.2 seconds in spec, decision, and code.
