---
id: T-002
name: Resolve incognito and undo product policy
status: done
workstream: WS-A
created: 2026-08-04T13:27:38Z
updated: 2026-08-04T21:34:51Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: []
conflicts_with: []
parallel: true
priority: high
estimate: S
story_id: US-009
acceptance_criteria_ids: [AC-007, AC-020, AC-035, AC-042]
---

# Task: Resolve incognito and undo product policy

## Description

Obtain and record explicit product decisions for provider-backed AI in Ownkey incognito mode and whether the first delivery includes a persistent custom undo affordance beyond preview-before-replace and host/keyboard undo.

## Acceptance Criteria

- [x] The decisions log contains one accepted decision for incognito behavior, including secure-field distinction, provider disclosure, and zero-content-telemetry rationale. — D-007, 2026-08-04.
- [x] The decisions log contains one accepted decision for first-release persistent undo scope and its recovery implications. — D-011, 2026-08-04.
- [x] The spec's Needs Clarification, Scope, acceptance scenarios, functional requirements, and rollout/rollback text are reconciled with both decisions.
- [x] No implementation task remains ambiguous about whether to expose the feature in incognito mode or build a persistent undo control. Incognito enforcement is assigned to T-020; bespoke persistent undo is explicitly out of scope.

## Traceability

- Story: US-009 and US-003.
- Acceptance criteria: AC-007, AC-020, AC-035, and AC-042; this task primarily closes NC-001 and NC-002.

## Technical Notes

Incognito is not equivalent to a secure/password field. The decision must accurately describe configured cloud-provider transfer and must not claim local processing. Persistent undo should be evaluated against the mandatory preview and stale-target safeguards rather than treated as a substitute for them.

## Definition of Done

- [x] Product decisions accepted.
- [x] Canonical spec and decisions updated.
- [x] Downstream ambiguity removed.
- [x] `delano validate` passes.

## Evidence Log

- 2026-08-04T21:34:51Z: D-007 governs incognito; D-011 defers bespoke persistent undo and preserves preview, target revalidation, copy fallback, and host undo.

- 2026-08-04T21:29:31Z: Record and reconcile accepted persistent-undo policy

- 2026-08-04T21:29:31Z: Product owner decided to defer persistent undo from the first release

- 2026-08-04: Task created as an external product-decision blocker.
- 2026-08-04: Incognito policy resolved. Product review accepted D-007 — incognito disables every cloud AI action (dictation, preset rewrite, voice rewrite) with a visible disabled state and a route out of incognito. NC-001 closed; spec, plan, and task graph reconciled; enforcement assigned to T-020. This task remains blocked on the persistent-undo decision (NC-002) alone.
