---
name: Autocorrect engine rebuild
slug: autocorrect-engine
owner: ownkey-keyboard-team
created: 2026-09-24T11:09:21Z
updated: 2026-09-24T11:31:43Z
---

# Decisions: Autocorrect engine rebuild

## Active decisions

- 2026-09-24: Replace the scoring instead of retuning thresholds. At the most aggressive user settings the current engine corrects under 3% of typos and about a quarter of those corrections are wrong. A log-frequency version of the same formula still fires 0% at defaults. Evidence: `research/baseline-2026-09-24.md`.
- 2026-09-24: Use a noisy-channel posterior (frequency prior times error likelihood) with the literal input as a competing candidate. The untuned reference reached 59% (EN) and 45% (NL) right corrections on synthetic typos with 1.2% or fewer wrong. It does not meet every Phase 1 gate yet: the real-world lists, the out-of-dictionary set and rare-word precision need calibration on non-circular data.
- 2026-09-24: Keep the current candidate indexes for Phase 1 and make a trie or binary dictionary conditional. The independent review measured the same results from noisy-channel scoring on the current indexes as from a full-vocabulary, distance-2 search. Triggers for the conditional phase are listed in `plan.md`.
- 2026-09-24: Order the work by relief per effort: toggle fix, input-keyed commit path, scoring swap, curated dictionary removals. The commit path goes before the scoring swap, because the stale-candidate race only causes wrong corrections once corrections become eligible.
- 2026-09-24: Autocorrect triggers only on space and token-ending sentence punctuation. It never triggers on apostrophe, hyphen, digits, `@` or `/`, and enter keeps today's no-autocorrect behavior.
- 2026-09-24: Precision wins over recall when gates conflict. The recall gate then moves to the phase that adds context or touch data.
- 2026-09-24: Keep all autocorrect on device and out of the network path. The AI rewrite feature already covers sentence-level fixes, and a network call per space press would break the "AI must not block typing" rule in `CLAUDE.md`.
- 2026-09-24: Create a new implementation project instead of extending `predictive-typing-quality-trust`. That project is planning-only by its own decision log. This project makes its T-001 (benchmark) and T-003 (trust-first autocorrect policy) concrete. Its other tasks stay where they are.
- 2026-09-24: Keep the recovery features from `typing-speed-core` (T-004 undo, T-011 never-correct, T-012 backspace restore, T-015 revert memory). Replace the T-003 confidence formula and the T-013 slider surface.
- 2026-09-24: Leave out Linear sync for now. The plan should be reviewed before it creates external issues.

## Superseded decisions

- `typing-speed-core` T-003: "High-certainty autocorrect policy" with `minConfidence` 0.88 on a linear-frequency confidence score. Superseded by posterior thresholds once T-004 lands.
- 2026-09-24 (this project, first draft): "One frequency trie replaces the delete index and the prefix index." Replaced by the conditional trie decision above after the independent review.

## Open decision questions

1. Bigram corpus and size budget for Phase 3. Options: OpenSubtitles bigrams through OPUS (closest to chat register, license needs checking per sub-corpus), Wikipedia (CC BY-SA, formal register), Tatoeba sentences (CC BY, small and conversational). Recommendation: probe OpenSubtitles plus Tatoeba first, with a budget of at most 3 MB compressed per language.
2. Default strength once Phase 1 passes. Recommendation: Normal, because the precision gate (97% or better) already encodes the trust requirement, and Gentle would hide most of the improvement.
3. Validity whitelists for Phase 2. Recommendation: SCOWL for English and OpenTaal for Dutch. Both have permissive licenses that look compatible with Apache-2.0, but that needs a check before they ship.
4. Whether to mark `predictive-typing-quality-trust` T-001 and T-003 as superseded, and whether to sync this project to Linear. Both are delivery-state changes outside this project, so they wait for Bart.
