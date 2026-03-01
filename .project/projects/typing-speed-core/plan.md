---
name: Typing Speed Core
status: planned
lead: ownkey-keyboard-team
created: 2026-02-25T19:38:38Z
updated: 2026-02-25T19:38:38Z
linear_project_id:
---

# Delivery Plan: Typing Speed Core

## Architecture Decisions
- Separate low-latency prediction path from heavier personalization updates.
- Introduce confidence policy layer for autocorrect decisions.
- Model adaptation as composable policies: language policy + app-context policy + user override policy.
- Add explicit correction-history state to support one-tap undo and smart backspace.

## Workstream Design
- **WS-1 Prediction Engine Latency Foundation**: optimize inference/ranking path and warm-start behavior.
- **WS-2 Autocorrect Control and Recovery UX**: confidence gating, undo, backspace intelligence, tuning controls.
- **WS-3 Personalization and Multilingual Adaptation**: rapid learning, mixed-language handling, per-language dictionaries.
- **WS-4 Context Policy + Metrics**: app-specific profiles, KPI instrumentation, and feedback loops.

## Milestone Strategy
1. M1 (Engine baseline + instrumentation): establish p95 latency baseline and optimize top-3 pipeline.
2. M2 (Correction trust): ship high-certainty autocorrect + one-tap undo + smart backspace.
3. M3 (Adaptation depth): ship rapid personal learning + mixed-language + per-language dictionary isolation.
4. M4 (Context tuning + UX polish): app-specific profiles, never-correct list, tuning screen, symbol/number flow improvements.

## Rollout Strategy
- Feature-flag high-impact behavior changes.
- Stage rollout by internal builds -> beta users -> wider release.
- Monitor KPI deltas and rollback if false-correction ratio regresses.

## Test Strategy
- Unit tests for ranking/confidence policies and dictionary partitioning.
- Integration tests for autocorrect undo and smart backspace state transitions.
- Macrobenchmark/perf tests for suggestion latency and startup responsiveness.
- Manual QA matrix for EN/NL/mixed-language and app-context scenarios.

## Rollback Strategy
- Keep policy flags to disable app-context aggressiveness and rapid learning independently.
- Fall back to conservative autocorrect thresholds if false corrections increase.
- Preserve existing stable prediction path behind feature gate for emergency revert.

## Prioritized Build Backlog (Engine -> UX -> Metrics/Logging)
1. Build keystroke-to-suggestion instrumentation and capture baseline p95 latency + acceptance metrics.
2. Optimize top-3 prediction path with caching/warm path until p95 < 50 ms.
3. Implement high-certainty autocorrect thresholds with conservative defaults.
4. Add one-tap undo for last autocorrect and corrected-token history model.
5. Implement smart backspace restoration behavior for corrected words.
6. Add rapid user-vocabulary promotion (1-3 repeats) with decay/quality guardrails.
7. Add mixed-language token scoring without manual language switch.
8. Add per-language personal dictionary partitioning and migration.
9. Add app-specific autocorrect aggressiveness policy (chat vs mail profiles).
10. Add never-correct word list and settings controls.
11. Add prediction tuning UI (Conservative/Balanced/Aggressive).
12. Improve symbols/numbers flow to reduce mode-switches while preserving prediction continuity.
13. Optimize keyboard open + first input path to meet <200 ms perceived responsiveness.
14. Validate KPI trajectory and adjust thresholds before broad rollout.
