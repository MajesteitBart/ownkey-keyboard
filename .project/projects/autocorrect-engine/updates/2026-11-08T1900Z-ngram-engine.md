---
timestamp: 2026-11-08T18:59:38Z
status: in-progress
task: T-021
stream: WS-A
---

# Progress Update

## Completed
- Ported the n-gram engine (`lib/prediction`, interpolated Kneser-Ney trigram models for en and nl, memory-mapped from the APK) onto 0.11.0 as `NgramLatinScorer`, the new default for en, nl and nl+en. Main's layers stay on top: chat shorthand, never-correct words, apostrophe forms, offensive lists, trigger and field policies, undo.
- Added `LatinPipelineReplayTest`, which replays the dev tap plans through the app's real autocorrect pipeline and fails if it falls more than 0.1 points behind the engine alone.
- Phone benchmark against Gboard (protocol and numbers in `tools/kbbench/RESULTS.md`): keystroke savings 55.3% vs 41.5% (1.33x), word error rate after typos 0.86% vs 2.01% (0.43x). 0.11.0 measured 45.1% and 4.02%.
- `:lib:prediction:test` 154 passed; `:app:testDebugUnitTest` 1133 passed, 1 skipped.

## In Progress
- Release to the production track.

## Blockers
- None. Open follow-ups: Dutch keystroke savings (1.19x Gboard), the three #17 gates the n-gram scorer misses, APK size (106 MB arm64).
