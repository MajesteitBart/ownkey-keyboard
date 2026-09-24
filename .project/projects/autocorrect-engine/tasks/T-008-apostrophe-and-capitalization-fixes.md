---
id: T-008
name: Apostrophe and capitalization fixes
status: ready
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-24T20:13:47Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-007]
conflicts_with: []
parallel: false
priority: medium
estimate: S
story_id: US-005
acceptance_criteria_ids: [AC-006]
---

# Task: Apostrophe and capitalization fixes

## Description

Add a per-language table of apostrophe-less forms as an extra candidate source. Unambiguous forms autocorrect through the normal posterior gate. Ambiguous forms are suggestion-only until Phase 3 context exists. English standalone `i` becomes "I".

## Acceptance Criteria

- [ ] Unambiguous EN forms autocorrect: `dont`, `im`, `youre`, `thats`, `didnt`, `doesnt`, `isnt`, `ive`, `wasnt`, `couldnt`, `wouldnt`, `shouldnt`, `havent`, `arent` (AC-006).
- [ ] Ambiguous EN forms (`ill`, `id`, `wont`, `cant`, `were`, `its`, `well`, `hell`, `shell`, `lets`) only show the apostrophe form as a suggestion.
- [ ] NL: `zn` and `mn` suggest "z'n" and "m'n". Auto-commit only if the benchmark shows no rise in clean-text false corrections.
- [ ] Standalone English `i` becomes "I" on space. Never inside other words, and never on NL-primary subtypes unless the surrounding words score as English.
- [ ] The EN missing-apostrophe benchmark set reaches 14 of 14 in top-1 and at least 12 of 14 autocorrected.

## Traceability

- Story: US-005
- Acceptance criteria: AC-006

## Technical Notes

- A Dutch `i` typo for "is" or "in" must not be capitalized.
- The apostrophe itself must not trigger autocorrect (T-003), otherwise `isn` gets corrected before `isn't` is finished.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created from the missing-apostrophe benchmark set (0 of 14 today).
