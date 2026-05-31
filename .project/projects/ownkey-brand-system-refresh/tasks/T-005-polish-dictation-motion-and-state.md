---
id: T-005
name: Polish dictation motion and state
status: ready
created: 2026-05-31T16:45:00Z
updated: 2026-05-31T16:45:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: ["T-001"]
conflicts_with: ["dictation-ui", "ime-motion"]
parallel: false
priority: medium
estimate: M
---

# Task: Polish dictation motion and state

## Description
Refine dictation start, active, transcription, and exit states with short Ownkey-style motion and clear status cues.

## Acceptance Criteria
- [ ] Start/end transitions are visible but do not delay typing.
- [ ] Active recording state is unmistakable without occupying excessive keyboard height.
- [ ] Error/permission states use the same toast/snackbar language as the rest of the app.
- [ ] Motion respects reduced-animation expectations where Android APIs make that available.

## Technical Notes
The current slide/fade transition can be refined rather than replaced.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-05-31: Task created from Delano project plan.
