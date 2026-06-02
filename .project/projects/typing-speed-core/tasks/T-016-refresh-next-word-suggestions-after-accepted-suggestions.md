---
id: T-016
name: Refresh next-word suggestions immediately after accepted suggestions
status: review
created: 2026-03-28T18:35:00Z
updated: 2026-03-28T18:35:00Z
linear_issue_id:
github_issue:
github_pr: https://github.com/MajesteitBart/ownkey-keyboard/pull/7
depends_on: [T-014]
conflicts_with: []
parallel: true
priority: high
estimate: S
workstream: WS-1
---

# Task: Refresh next-word suggestions immediately after accepted suggestions

## Description
When a user taps a suggestion and OwnKey keeps a pending phantom separator instead of inserting a hard space, refresh the suggestion row immediately with follow-up next-word suggestions instead of waiting for the next literal keypress.

## Acceptance Criteria
- [x] After accepting a suggestion that leaves a pending separator, the next suggestion refresh treats that state like a next-word boundary immediately.
- [x] The fix is scoped to pending-separator refresh context and does not globally force extra refreshes for unrelated suggestion modes.
- [x] Focused tests cover the synthesized follow-up suggestion context.

## Technical Notes
Use the pending separator as the contract source of truth. The NLP layer should receive a virtual boundary context, not a generic forced refresh, so recent spacing, punctuation, undo/backspace recovery, and reverted-autocorrect suppression behavior stay intact.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log
- 2026-03-28: Root cause confirmed in `KeyboardManager.resetSuggestions()`: after accepted suggestions, the refresh path passed the literal editor snapshot to `nlpManager.suggest()`, so the NLP layer still saw the cursor attached to the committed word whenever phantom spacing was pending.
- 2026-03-28: Because next-word prediction in `LatinLanguageProvider` only activates on an actual boundary, suggestion acceptance could leave the row empty until the user typed a space or another letter.
- 2026-03-28: Added `PendingSeparatorSuggestionContent` so refreshes synthesize a virtual boundary only while `editorInstance.phantomSpace.isActive`, then wired `KeyboardManager.resetSuggestions()` to use that derived context.
- 2026-03-28: Added focused unit tests for pending-separator refresh behavior and re-ran full local unit + Delano validation successfully.
