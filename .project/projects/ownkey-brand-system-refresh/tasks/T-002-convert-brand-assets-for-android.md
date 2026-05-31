---
id: T-002
name: Convert brand assets for Android and store use
status: ready
created: 2026-05-31T16:45:00Z
updated: 2026-05-31T16:45:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: ["T-001"]
conflicts_with: ["brand-assets", "store-assets"]
parallel: true
priority: high
estimate: M
---

# Task: Convert brand assets for Android and store use

## Description
Convert the source SVGs into the formats required by Android resources and store metadata while keeping the source SVGs as canonical assets.

## Acceptance Criteria
- [ ] App icon keycap source SVG is preserved at `assets/branding/ownkey-app-icon-keycap.svg`.
- [ ] Standalone mark source SVG is preserved at `assets/branding/ownkey-monkey-waveform-mark.svg`.
- [ ] Android drawable/vector or raster outputs are generated only where they are actually needed.
- [ ] Store icon/feature candidates are traceable back to source assets.

## Technical Notes
Avoid hand-editing generated vector/raster files unless the converter output needs a small compatibility fix.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-05-31: Source SVGs saved in `assets/branding/`.
