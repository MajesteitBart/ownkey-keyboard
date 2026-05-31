---
id: T-006
name: Lock toast snackbar routing and style
status: ready
created: 2026-05-31T16:45:00Z
updated: 2026-05-31T16:45:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: ["T-001"]
conflicts_with: ["toast-routing", "ime-overlay"]
parallel: false
priority: high
estimate: M
---

# Task: Lock toast snackbar routing and style

## Description
Make Ownkey toast/snackbar messages consistently appear in the correct host: keyboard messages inside the IME surface, app/settings messages inside the app window.

## Acceptance Criteria
- [ ] Keyboard action messages appear while typing in other apps.
- [ ] Settings messages do not get routed into a hidden/stale IME overlay.
- [ ] IME toast is bottom-positioned, compact, dismissible, and above keyboard content.
- [ ] Styling supports neutral, success, warning, and error states.

## Technical Notes
Recent fixes moved IME messages back into the keyboard root. Keep routing explicit and test with both app and IME contexts.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-05-31: Task created from Delano project plan.
