---
id: T-009
name: Redesign secondary action bar to match reference
status: ready
created: 2026-06-03T09:49:26Z
updated: 2026-06-03T09:49:26Z
linear_issue_id:
github_issue:
github_pr:
depends_on: ["T-004"]
conflicts_with: ["ime-smartbar", "quick-actions", "visual-qa"]
parallel: false
priority: high
estimate: M
workstream: WS-C
story_id:
acceptance_criteria_ids: []
---

# Task: Redesign secondary action bar to match reference

## Description
Correct the secondary action bar redesign using the clarified visual read: the current/screenshot bar is too small and cramped, while the design/reference bar is taller, more substantial, and easier to use.

The target is not a compact toolbar. The target is a larger secondary action bar that lives inside the keyboard's reserved IME area, with enough vertical room for the pill and action buttons, without overlapping the host app/page UI.

## Acceptance Criteria
- [ ] Secondary action bar height and internal vertical padding are closer to the taller design/reference than to the smaller current screenshot.
- [ ] Action buttons have usable hit targets and no cramped icon/text treatment.
- [ ] The secondary action bar is part of the reserved keyboard height and does not overlay host app/page content.
- [ ] Candidate row, down button, mic button, and secondary action bar align as a coherent vertical composition.
- [ ] The implementation handles expanded/default action state without layout jump or hidden content.
- [ ] Visual QA includes screenshot comparison against the reference and at least one host app/page where overlap would be obvious.

## Traceability
- Story: none
- Acceptance criteria: Corrected secondary action bar reference from Mattermost thread, 2026-06-03.

## Technical Notes
Do not repeat the earlier mix-up. The screenshot/current bar is the smaller/cramped bar. The design/reference bar is the larger/substantial target.

Treat overlap and height as separate requirements:
- The bar must be larger and more usable like the design/reference.
- The IME must reserve enough height so that larger bar does not cover the app/page behind it.

Audit the sizing path end to end instead of changing only the visible button/pill dimensions. Check the measured secondary action row height, reserved keyboard height, smartbar/candidate row composition, and expanded/default action placement.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-06-03: Task created from clarified design comparison after the previous read was reversed.
