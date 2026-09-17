---
type: research_intake
project: orukeet-offline-dictation
slug: android-integration
owner: ownkey-keyboard-team
status: completed
created: 2026-09-15T10:30:31Z
updated: 2026-09-15T10:40:57Z
---

# Research plan: Optional Orukeet Android integration

## Goal

Create an evidence-backed plan for explicitly downloaded and activated local ASR in Ownkey.

## Primary Question

How can Ownkey add optional local transcription while preserving typing responsiveness, provider choice, and editor safety?

## Scope

Inspect existing Android flows and primary upstream/platform sources. Define lifecycle, integration, device-probe gates, release checks, and rollback. Implementation, model download, device benchmarking, and external publishing are outside this planning task.

## Current Phase

Research complete; conclusions folded into planned contracts. Physical-device probe remains pending.

## Phases

- [x] Open research intake.
- [x] Inspect sources and implementation seams.
- [x] Summarize evidence, options, and unknowns.
- [x] Fold durable conclusions into spec, plan, decisions, and update note.

## Decisions Made

Use optional verified model delivery and explicit activation, preserve current routing until the user switches, and gate implementation on real-device feasibility. Details are in ../../decisions.md.

## Blockers

No blocker to the requested plan. Runtime viability and supported-device claims require the proposed probe.
