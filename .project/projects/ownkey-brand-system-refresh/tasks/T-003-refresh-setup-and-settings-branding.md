---
id: T-003
name: Refresh setup and settings branding
status: done
created: 2026-05-31T16:45:00Z
updated: 2026-05-31T17:05:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: ["T-001", "T-002"]
conflicts_with: ["settings-ui", "setup-ui"]
parallel: true
priority: high
estimate: M
workstream: WS-B
---

# Task: Refresh setup and settings branding

## Description
Apply the Ownkey visual system to setup, settings, About, and transcription configuration screens.

## Acceptance Criteria
- [x] Setup flow uses the Ownkey mark without adding a marketing-style landing page.
- [x] Settings hierarchy uses the brand palette while staying easy to scan.
- [x] About/transcription screens feel consistent with the rest of the app.
- [x] Android fallback toasts remain legible and bottom-positioned.

## Technical Notes
Use compact headers and restrained surfaces. Avoid nesting cards inside cards.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log
- 2026-05-31: Task created from Delano project plan.
- 2026-05-31: Setup header now uses `ic_ownkey_mark`; About uses `R.mipmap.ownkey_app_icon`; shared tokens back the setup palette.
