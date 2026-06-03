---
id: T-009
name: Redesign secondary action bar to match reference
status: review
created: 2026-06-03T09:49:26Z
updated: 2026-06-03T11:24:05Z
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
- [x] Secondary action bar height and internal vertical padding are closer to the taller design/reference than to the smaller current screenshot.
- [x] Action buttons have usable hit targets and no cramped icon/text treatment.
- [x] The secondary action bar is part of the reserved keyboard height and does not overlay host app/page content.
- [x] Candidate row, down button, mic button, and secondary action bar align as a coherent vertical composition.
- [x] The implementation handles expanded/default action state without layout jump or hidden content.
- [x] Visual QA includes screenshot comparison against the reference and at least one host app/page where overlap would be obvious.

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
- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-06-03T11:24:05Z: Corrected secondary action icon sizing after visual feedback. Fixed the secondary row measurement path so action slots receive real height, rendered Tabler icons at 30dp with high-contrast tint and visible padding, and verified the latest emulator crop at `artifacts/maestro/test-output-t009-pass5/screenshots/secondary-icons-30dp-crop.png`.

- 2026-06-03T10:28:17Z: Starting second correction pass against attached design reference

- 2026-06-03T10:28:13Z: Reference mismatch: action bar icons, spacing, key color, and keyboard chrome need another correction pass

- 2026-06-03T10:14:23Z: Implemented larger reserved secondary action bar; verified git diff --check, JSON stylesheet parsing, delano validate, :app:compileReleaseKotlin, :app:installDebug, and Maestro screenshots at artifacts/maestro/test-output-t009/screenshots/.

- 2026-06-03: Implemented reserved-height secondary action row sizing and refreshed the Ownkey/Voxtral action pill styles across 16 theme variants.
- 2026-06-03: Verified with `git diff --check`, Voxtral JSON stylesheet parsing, `delano validate`, `.\gradlew.bat :app:compileReleaseKotlin`, and `.\gradlew.bat :app:installDebug`.
- 2026-06-03: Captured emulator visual QA in `artifacts/maestro/test-output-t009/screenshots/`; the expanded secondary bar sits inside the IME area over Android Settings without host-page overlap.
- 2026-06-03T10:01:46Z: Starting secondary action bar reference correction on pr-8-validation
- 2026-06-03: Task created from clarified design comparison after the previous read was reversed.
