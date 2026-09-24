---
id: T-005
name: Curated dictionary removals
status: blocked
blocked_owner: ownkey-keyboard-team
blocked_check_back: After dependencies are done: T-001
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-24T11:31:43Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: []
parallel: true
priority: high
estimate: S
story_id: US-002
acceptance_criteria_ids: [AC-002]
---

# Task: Curated dictionary removals

## Description

Misspellings in the current word lists count as correct words and can never be corrected: `mischien` (1,043 hits) and `eigelijk` (286) in Dutch, `untill`, `seperate`, `occured` and `tommorow` in English. 13 of 100 English and 5 of 60 Dutch real-world misspellings in the benchmark are dictionary words. Remove them from the current assets with a reviewed list, before the Phase 2 pipeline exists.

## Acceptance Criteria

- [ ] A reviewed removal list per language, checked into the repo, covering known misspellings, backtick forms (`` don`t ``) and OCR single letters (`l`, `s`, `o`, `t` in English).
- [ ] Split contraction fragments (`don`, `didn`, `isn`) stay for now. They go in T-007, once contraction forms exist to replace them.
- [ ] Apostrophe-less forms (`dont`, `im`) stay until T-008 adds their replacements.
- [ ] The benchmark shows no word from the removal list as an exact match, and AC-002 passes with the T-004 scoring.
- [ ] The clean-text false-correction rate does not rise.

## Traceability

- Story: US-002
- Acceptance criteria: AC-002

## Technical Notes

- Apply the list at load time or as a small asset patch, whichever keeps T-007 simpler. T-007 later reuses the same list as an input.
- Removing words changes the calibration. Rerun the benchmark and adjust T-004 thresholds afterward.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created. Moved into Phase 1 after the independent review found that AC-002 cannot pass without it.
