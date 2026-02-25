---
name: Typing Speed Core
slug: typing-speed-core
owner: ownkey-keyboard-team
status: draft
created: 2026-02-25T19:38:38Z
updated: 2026-02-25T19:38:38Z
outcome: Deliver measurable typing-speed gains by reducing prediction latency and correction friction while preserving user control.
---

# Spec: Typing Speed Core

## Executive Summary
This project hard-filters keyboard behavior on typing speed by prioritizing low-latency top-3 predictions, high-certainty autocorrect, one-tap recovery, and fast personalization across mixed-language and app-specific contexts.

## Problem and Users
Fast typers and multilingual users lose throughput when predictions are delayed, autocorrect overreaches, and correction recovery is slow. Context mismatch (chat vs mail) increases correction friction and reduces trust.

## Scope
### In Scope
- Top-3 next-word prediction latency optimization (target p95 < 50 ms)
- High-certainty autocorrect policy and confidence gating
- One-tap undo for latest autocorrect
- Rapid personal vocabulary learning (1-3 exposures)
- Mixed-language sentence support without manual switching
- App-specific autocorrect aggressiveness profiles
- Keyboard startup and first input perceived performance (<200 ms)
- Faster symbol/number entry with fewer mode switches
- Per-language personal dictionary partitioning
- Never-correct word protection list
- Smart backspace recovery behavior
- Lightweight prediction tuning screen (Conservative/Balanced/Aggressive)

### Out of Scope
- Voice dictation feature expansion
- Provider/network ASR changes
- Non-typing-related visual redesign

## Functional Requirements
1. Render top-3 predictions in under 50 ms p95 from keystroke to visible suggestion update.
2. Apply autocorrect only when confidence exceeds configured high-certainty thresholds.
3. Provide one-tap undo for the most recent autocorrect operation.
4. Promote user-specific words/names quickly after 1-3 repeated usages.
5. Support mixed-language token handling in a single sentence.
6. Adjust autocorrect aggressiveness by app context (e.g., chat vs mail).
7. Ensure keyboard open + first input feels instant with <200 ms target.
8. Reduce mode-switch cost for symbols/numbers while preserving prediction continuity.
9. Keep separate personal dictionaries per language.
10. Support never-correct list entries for brands/names/jargon.
11. Make backspace context-aware for corrected tokens.
12. Provide a lightweight prediction tuning control (Conservative/Balanced/Aggressive).

## Non-Functional Requirements
- Privacy-first instrumentation: aggregate and non-content logging only.
- Performance regressions must be detectable via automated metrics checks.
- Feature defaults should prioritize control and low false-correction risk.

## Success Metrics
- Suggestion latency p95: < 50 ms
- False autocorrect ratio: -30% vs baseline
- Top-3 suggestion acceptance rate: +20% vs baseline
- Keystrokes per word: reduced vs baseline
- Undo autocorrect frequency: expected initial discovery increase, then sustained decline

## Risks and Assumptions
- Requires quality benchmark corpus across EN/NL + mixed-language sequences.
- Context detection policy must avoid unstable app-type classification.
- Personalization acceleration must avoid overfitting to temporary terms.

## Dependencies
- Existing suggestion/autocorrect engine in app module
- Settings and preferences UI extension points
- Metrics/logging pipeline with privacy-safe aggregate counters
