---
type: research_intake
project: personal-dictionary
slug: windows-parity
owner: ownkey-keyboard-team
status: completed
created: 2026-09-17T20:22:35Z
updated: 2026-09-17T20:44:23Z
---

# Research Plan: Windows Dictionary and Filler Cleanup Parity

## Goal and Primary Question

How should Android adapt Windows vocabulary hints, corrections, and filler removal without an LLM or typing regressions?

## Scope

Read both requested T3 threads, inspect Windows code and Android integration points, check runtime/provider contracts, and fold evidence into a proposed spec and delivery plan. No Android feature implementation, private transcript publication, or execution-task completion from research alone.

## Current Phase

Research completed and folded forward; device prototype remains explicitly pending in the planned spec.

## Phases

- [x] Open research intake.
- [x] Investigate both threads, Windows implementation, and Android architecture.
- [x] Summarize sources, observed behavior, options, and evidence limits.
- [x] Fold findings into `spec.md`, `plan.md`, and `decisions.md`.

## Decisions Made

Separate speech storage; three feature controls; ordinary-dictation-only cleanup; immutable session snapshots; Windows rule parity; explicit cloud hint capability; physical-device gate for local hotwords. See `decisions.md` for rationale.

## Blockers

None for research/planning. The future implementation needs physical-device decoder/transport qualification and exact provider-request tests; those are not marked complete.
