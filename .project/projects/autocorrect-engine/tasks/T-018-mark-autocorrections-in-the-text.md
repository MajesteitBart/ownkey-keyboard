---
id: T-018
name: Mark autocorrections in the text
status: done
workstream: WS-A
created: 2026-09-24T22:22:47Z
updated: 2026-09-24T23:22:07Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: false
priority: medium
estimate: S
story_id: US-003
acceptance_criteria_ids: []
---

# Task: Mark autocorrections in the text

## Description

After an autocorrection, the text field should show that a word was changed and let the user get the original back, the way Android keyboards do it: the corrected word is committed with a suggestion span flagged as an autocorrection that carries the typed word.

## Acceptance Criteria

- [x] Auto-committed corrections are committed with `SuggestionSpan.FLAG_AUTO_CORRECTION` and the original word as its suggestion. How editors show it varies (see the evidence log), so the keyboard also offers the typed word itself: right after an autocorrection it is the first chip in the suggestion strip, and one tap restores it.
- [x] Manual suggestion taps, casing-only changes ("I") and apostrophe forms keep committing plain text or follow the same rule consistently; nothing else about committing changes.
- [x] Backspace right after an autocorrection still restores the typed word (AC-004).
- [x] Checked in a standard Android text field and in Chrome on the emulator; editors that ignore the span behave as before.

## Traceability

- Story: US-003
- Acceptance criteria: none (Phase 5 in plan.md)

## Technical Notes

- The span is a platform mechanism: the editor draws the underline and handles the tap. Editors that do not support it simply ignore it.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: Review of T-016 to T-019 found the undo tracker stayed armed after the correction: typing on and ending with the same word again ("the") brought back the chip, and backspace or the chip then replaced a correctly typed word and put the typed word on the never-correct list; backspace could also swallow a period and two line breaks, and a selection gave the wrong range. The tracker now remembers where the corrected word ends and only allows undo with the cursor there or one separator after it and no selection; any other key or a new field clears it. The cursor fix also failed for same-length corrections (teh/the), because the editor state it read was stale; the undo now replaces the word and the separator in one commit. Emulator: after `teh` -> "the" and more typing ending in "the ", backspace deleted the space only; `adn` -> "and", chip -> "adn " and typing went on after the space; `wrld` -> "world", backspace -> "wrld". The tracker state is now volatile for the chip, which reads it off the main thread. Regression tests in `AutocorrectUndoTrackerTest`.
- 2026-09-24: Auto-commits now reach the editor as the corrected word with a `SuggestionSpan` (`FLAG_AUTO_CORRECTION`, suggestion: the typed word); manual taps, casing-only changes ("i" to "I") and everything else commit plain text as before. The span is passed through `finalizeComposingText` and `commitText` as styled text while the editor bookkeeping keeps using the plain string.
- 2026-09-24: Emulator: Chrome draws a blue underline under the corrected word ("store" for `stoer`) that stays until the text is edited, but tapping the word only places the cursor. The Google Contacts notes field (a standard Android field) shows no lasting underline once typing continues and offers no popup either. So the span alone does not give "tap to restore". Added `AutocorrectRevertCandidate`: while the last autocorrection can be undone, the typed word leads the suggestion strip with an undo icon ("↶becuase"), and tapping it runs the same undo as the toolbar undo, which also puts the word on the never-correct list.
- 2026-09-24: Found and fixed while testing: undo replaced only the word, so the cursor ended between the restored word and the space after it, and the next word ran on ("stoerand"). The chip and toolbar undo now keep the space and put the cursor back after it ("becuase it was"); backspace takes the space as well and leaves the cursor right after the restored word, as other keyboards do (`thsi` -> "this " -> backspace -> "thsi", then "x" gives "thsix"). Full `ime` suite: 546 tests pass (the keyboard and editor code is Android-only and was checked on the emulator).
- 2026-09-24: Task created with Phase 5.
