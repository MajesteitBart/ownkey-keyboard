---
timestamp: 2026-09-24T11:31:43Z
status: planned
task: T-001
stream: WS-A
---

# Progress Update

## Completed
- Measured the current autocorrect with a JavaScript port of the Kotlin scoring against the shipped EN and NL dictionaries. At defaults it corrected 0 of 2,280 synthetic touch typos and 0 of 160 real-world misspellings. At the most aggressive slider settings it corrected under 3%, and about a quarter of those corrections were wrong. Details and root causes: `research/baseline-2026-09-24.md`.
- Ran an untuned noisy-channel reference scorer on the same data: 59% (EN) and 45% (NL) of synthetic typos corrected with 1.2% or fewer wrong, and 41% of real-world English misspellings with none wrong.
- Independent review reproduced every baseline number and confirmed the port against the Kotlin code. It found that the scoring swap alone matches a full candidate-search rebuild, that the reference does not meet every drafted Phase 1 gate, that `mischien` is a dictionary word, that autocorrect triggers on apostrophes and `@`, and that completions can never autocorrect.
- Revised spec, plan and decisions accordingly: trie rebuild made conditional, quick-relief order, provisional gates re-derived on non-circular data, curated dictionary removals moved into Phase 1. Tasks T-001 to T-008 cover Phases 0 to 2.

## In Progress
- None.

## Blockers
- None for T-001, T-002 and T-003. The other tasks wait on their dependencies.

## Next Actions
- Bart reviews the plan and the open questions in `decisions.md`.
- Start T-001 (benchmark), then T-002 (toggle) and T-003 (commit path) in sequence, since both edit `KeyboardManager.kt`.
