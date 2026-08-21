---
id: T-015
name: Build voice processing review and recovery UI
status: done
workstream: WS-D
created: 2026-08-04T13:27:38Z
updated: 2026-08-05T13:06:25Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-010, T-011, T-014]
conflicts_with: []
parallel: false
priority: high
estimate: L
story_id: US-003
acceptance_criteria_ids: [AC-014, AC-015, AC-016, AC-017, AC-018, AC-019, AC-020, AC-024, AC-025, AC-042]
---

# Task: Build voice processing review and recovery UI

## Description

Extend the rewrite panel with voice-specific transcribing, rewriting, result review, retry, rerecord, safe replacement, copy fallback, success, and error/recovery surfaces while keeping the keyboard height stable.

## Acceptance Criteria

- [x] Transcribing shows labelled `Understanding instruction…` progress and separate cancel; the center input meter is no longer shown as if audio were active.
- [x] Rewriting shows the recognized instruction in a bounded accessible chip and a distinct `Rewriting selected text…` state.
- [x] Result review shows the recognized instruction, scrollable generated result, back, Try again, Record instruction again, and primary `Replace` action.
- [x] Retry and rerecord invoke their exact session semantics without changing the original valid target; the spoken instruction is never inserted into the editor.
- [x] Target mismatch/commit failure preserves the result and changes the action surface to Copy result and Close with no automatic insertion.
- [x] Successful replacement shows `Text replaced` confirmation and closes according to the approved timing without creating a persistent Ownkey undo surface or recovery payload; errors provide specific recovery and do not mutate source text.
- [x] Back, cancel, panel toggle, field loss, keyboard hide, and disposal cancel jobs and return to the correct entry origin without changing source text.
- [x] Renderer/state tests cover every processing, result, retry, rerecord, replace, mismatch, copy, success, error, and dismissal branch.

## Traceability

- Story: US-003 and US-006.
- Acceptance criteria: AC-014, AC-015, AC-016, AC-017, AC-018, AC-019, AC-020, AC-024, AC-025, and AC-042.

## Technical Notes

Keep the options grid as the stable base layer and state-hoist all voice behavior. The original source text must not be rendered in the keyboard. Result and instruction exist only while the captured editor session is valid.

## Definition of Done

- [x] Voice state surfaces implemented.
- [x] Review/recovery interactions wired.
- [x] Lifecycle cancellation verified.
- [x] Renderer/state tests pass.

## Evidence Log

- 2026-08-05T13:06:25Z: Added the voice transcribing, rewriting, review, copy-fallback and replacement-confirmation surfaces to the rewrite panel over the stable preset grid. Processing states show labelled progress with a separate cancel and no active input meter; the recognized instruction appears in a bounded two-line chip that is exposed in full to TalkBack and carries the Record instruction again mic action. Review shows a scrollable result over a fixed rail of Back, Try again and Replace; a failed target verification keeps the result and swaps the rail for Copy result and Close with no automatic insertion. A confirmed replacement shows Text replaced for 900 ms and then leaves the panel, creating no persistent undo control or stored result, instruction, or target history. Panel disposal, field switch and keyboard hide invalidate the session without touching source text. Evidence: 256 app debug unit tests passed (0 failures), including 12 new processing, result, mismatch, pipeline-error, success and lifecycle-invalidation cases; :app:compileDebugKotlin passed.

- 2026-08-05T13:01:38Z: Building voice processing, review, replace and recovery surfaces

- 2026-08-05T13:01:37Z: T-010, T-011 and T-014 done

- 2026-08-04: Task created during delivery decomposition.
