---
id: T-003
name: Probe provider options and privacy model
status: ready
created: 2026-05-01T11:07:28Z
updated: 2026-05-01T11:07:28Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-002]
conflicts_with: []
parallel: true
priority: high
estimate: M
---

# Task: Probe provider options and privacy model

## Description
Compare MVP cloud provider options with ML Kit Proofreading/Rewriting and on-device Gemini/AICore paths.

## Acceptance Criteria
- [ ] Cloud provider MVP recommendation is documented.
- [ ] ML Kit Proofreading/Rewriting support, language coverage, device availability, and beta risks are documented.
- [ ] Privacy model covers consent, key storage, no text logging, request minimization, and provider disabling.
- [ ] Provider abstraction contract is sketched.

## Technical Notes
ML Kit Proofreading has a voice input type and may fit the grammar-cleanup use case better than general rewriting. Rewriting styles can be later expansion.

## Definition of Done
- [ ] Findings added to spec Probe Findings
- [ ] Provider interface notes added to plan or design note
- [ ] Go/no-go recommendation written

## Evidence Log
- 2026-05-01: Initial research found ML Kit Proofreading/Rewriting and existing encrypted secret storage pattern.
