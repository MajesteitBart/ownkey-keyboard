---
id: T-007
name: Align store and metadata assets
status: completed
created: 2026-05-31T16:45:00Z
updated: 2026-05-31T17:05:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: ["T-002", "T-003", "T-004", "T-005", "T-006"]
conflicts_with: ["fastlane-assets", "release-metadata"]
parallel: true
priority: medium
estimate: M
---

# Task: Align store and metadata assets

## Description
Update store-facing assets and metadata screenshots so Ownkey looks consistent outside the APK too.

## Acceptance Criteria
- [x] Fastlane icon and feature graphic candidates use the keycap icon/mark.
- [x] Screenshots reflect the refreshed setup/settings/keyboard styling.
- [x] Store copy does not overpromise visual or transcription behavior.

## Technical Notes
Do this after UI changes so screenshots do not become stale immediately.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log
- 2026-05-31: Task created from Delano project plan.
- 2026-05-31: Generated Fastlane icon and feature graphic PNGs from the keycap/mark SVGs and tightened English short descriptions to avoid broad privacy claims.
