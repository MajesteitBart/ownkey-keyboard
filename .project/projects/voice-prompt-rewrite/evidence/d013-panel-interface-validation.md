# D-013 panel interface validation

Evidence for the dictation and rewrite interface change (D-013): voice rewrite renders as one
state-driven body inside the AI rewrite panel, the smartbar recording row is dictation-only, and
dictation inserts its own word boundary.

## Automated checks (2026-09-08)

| Command | Result |
| --- | --- |
| `gradlew.bat :app:testDebugUnitTest --console=plain` | 310 passed, 0 failed, 0 skipped |
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
