---
timestamp: 2026-08-04T17:01:03Z
status: complete
task: T-002
stream: scoped-change
---

# Progress Update

## Completed

- Accepted D-007: incognito mode disables every cloud AI action — dictation, preset rewrite, and voice rewrite — with a visible disabled state and a route out of incognito rather than a silent block. Closes NC-001.
- Recorded the current baseline in the spec: incognito is a learning-suppression flag only, resolved per session in `EditorInstance` and consumed by `NlpManager`. Neither the dictation nor the rewrite package references incognito or secure fields today, so both gates are new work.
- Reconciled the spec: new US-015, AC-035, AC-036, FR-053 through FR-056, an incognito preflight step, an error-matrix row, an in-scope bullet, an AC-027 carve-out, and a corrected ordinary-dictation out-of-scope line.
- Added AD-009 to the plan: one shared availability policy consumed by all three AI entry points instead of a per-manager check.
- Created T-020 to implement and enforce the gate, and wired it into T-009 as a dependency.
- Extended T-014 (disabled-state recovery UI), T-016 (incognito and `FORCE_ON` copy), and T-019 (verification that shipped copy matches enforced behavior).

## In Progress

- T-002 remains blocked on NC-002, the persistent-undo decision. Its incognito acceptance criterion is now satisfied.
- Spec and plan remain `planned` with `probe_status: pending`; this decision does not activate delivery.

## Blockers

- Persistent undo scope for the first release is still undecided and continues to block T-002, and therefore T-003 and all production work.

## Next Actions

- Resolve NC-002 with the product owner to unblock T-002.
- Execute T-001, which remains the only dependency-safe task.
- Run T-003 only after both inputs pass.

## Notes

- The gate deliberately changes ordinary dictation and preset rewrite availability. The main delivery risk is dynamic incognito, which host apps switch on through `flagNoPersonalizedLearning` without user intent; the disabled-state copy is the only thing separating "private" from "broken".
