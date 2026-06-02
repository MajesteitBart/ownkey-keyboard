---
id: T-002
name: Convert brand assets for Android and store use
status: done
created: 2026-05-31T16:45:00Z
updated: 2026-05-31T17:05:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: ["T-001"]
conflicts_with: ["brand-assets", "store-assets"]
parallel: true
priority: high
estimate: M
workstream: WS-A
---

# Task: Convert brand assets for Android and store use

## Description
Convert the source SVGs into the formats required by Android resources and store metadata while keeping the source SVGs as canonical assets.

## Acceptance Criteria
- [x] App icon keycap source SVG is preserved at `assets/branding/ownkey-app-icon-keycap.svg`.
- [x] Standalone mark source SVG is preserved at `assets/branding/ownkey-monkey-waveform-mark.svg`.
- [x] Android drawable/vector or raster outputs are generated only where they are actually needed.
- [x] Store icon/feature candidates are traceable back to source assets.

## Technical Notes
Avoid hand-editing generated vector/raster files unless the converter output needs a small compatibility fix.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log
- 2026-05-31: Source SVGs saved in `assets/branding/`.
- 2026-05-31: Added Android vector/adaptive icon resources, compact mark drawable, and generated store icon/feature PNG candidates from the source SVGs.
