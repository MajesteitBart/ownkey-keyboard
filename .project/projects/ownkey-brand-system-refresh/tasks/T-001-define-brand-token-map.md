---
id: T-001
name: Define Ownkey brand token map
status: completed
created: 2026-05-31T16:45:00Z
updated: 2026-05-31T17:05:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: ["theme-tokens", "brand-assets"]
parallel: true
priority: high
estimate: M
---

# Task: Define Ownkey brand token map

## Description
Extract the useful palette, type, shape, elevation, and motion decisions from the downloaded brand-book reference and map them to Android implementation targets.

## Acceptance Criteria
- [x] Token map documents color roles, typography mapping, shape scale, and motion timings.
- [x] Token names avoid raw brand-book-only naming when Android usage needs clearer roles.
- [x] IME-specific constraints are called out separately from app/settings tokens.

## Technical Notes
Use `docs/brandbook/ownkey-delano-brand-reference-2026-05-31.html` as the reference. Do not embed the HTML into Android.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log
- 2026-05-31: Task created from Delano project plan.
- 2026-05-31: Completed via `docs/brandbook/ownkey-brand-token-map-2026-05-31.md` and `OwnkeyBrand` Compose token helper; compile validation passed.
