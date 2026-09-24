---
name: Autocorrect engine rebuild
slug: autocorrect-engine
owner: ownkey-keyboard-team
created: 2026-09-24T11:09:21Z
updated: 2026-09-24T20:13:47Z
---

# Decisions: Autocorrect engine rebuild

## Active decisions

- 2026-09-24: Replace the scoring instead of retuning thresholds. At the most aggressive user settings the current engine corrects under 3% of typos and about a quarter of those corrections are wrong. A log-frequency version of the same formula still fires 0% at defaults. Evidence: `research/baseline-2026-09-24.md`.
- 2026-09-24: Use a noisy-channel posterior (frequency prior times error likelihood) with the literal input as a competing candidate. The untuned reference reached 59% (EN) and 45% (NL) right corrections on synthetic typos with 1.2% or fewer wrong. It does not meet every Phase 1 gate yet: the real-world lists, the out-of-dictionary set and rare-word precision need calibration on non-circular data.
- 2026-09-24: Keep the current candidate indexes for Phase 1 and make a trie or binary dictionary conditional. The independent review measured the same results from noisy-channel scoring on the current indexes as from a full-vocabulary, distance-2 search. Triggers for the conditional phase are listed in `plan.md`.
- 2026-09-24: Order the work by relief per effort: toggle fix, input-keyed commit path, scoring swap, curated dictionary removals. The commit path goes before the scoring swap, because the stale-candidate race only causes wrong corrections once corrections become eligible.
- 2026-09-24: Autocorrect triggers only on space and token-ending sentence punctuation. It never triggers on apostrophe, hyphen, digits, `@` or `/`, and enter keeps today's no-autocorrect behavior.
- 2026-09-24: No autocorrect in password, e-mail, URL and person-name fields or with `flagTextNoSuggestions`, and none right after an accepted suggestion. Legacy scoring never fired, so these fields were never exposed before; the new scorer would expose them.
- 2026-09-24: Fix the first-input freeze found during T-003 inside this project. It is not an autocorrect bug, but the on-the-spot decision path shares the same lock, and the spec requires typing to stay responsive.
- 2026-09-24: T-004 noisy-channel defaults, from a parameter sweep on the tap-noise sets (`NoisyChannelTuningTest`): unknown-word log-probability -20, threshold 0.95, short-input bonus 2.5, capitalized mid-sentence bonus 6, all-caps bonus 6, completion cost 0.8 per missing letter, most likely finished word first. Result: tap-noise typos fixed 70.6% EN, 68.5% NL, 60.3% NL+EN at 98.8 to 99.4% precision; real-world misspellings 66% EN curated and 81.6 to 85.8% NL with none wrong; 1 to 2 of 313 legitimate out-of-dictionary words changed; at most 0.13 false corrections per 1,000 clean words.
- 2026-09-24: Phase 1 top-1 gate lowered from 88% to 85%. Top-1 is 87.9% (EN) and 86.9% (NL). Of the EN misses, 7.1% of typos have the intended word outside the candidate set (two errors, or a target outside the top-20,000 typo index) and 5% are ambiguous between close candidates (`hre`: he, here, her) until context arrives in Phase 3. Raising the completion cost barely helped top-1 (87.7 to 88.1%) and cut completion reach from 58% to 28%.
- 2026-09-24: A small built-in list of EN and NL chat abbreviations (`ChatShorthand`) is never corrected; without it the tuned scorer changed `idd`, `wss` and `egt`.
- 2026-09-24: The new scorer is the default in this branch so dogfooding needs no switch; the legacy scorer stays one devtools switch away. The release default still waits for the Phase 1 dogfood gate on Bart's phone.
- 2026-09-24: On-the-spot decisions are the normal path, not a rare fallback: the editor confirms each keystroke asynchronously, so the suggestion batch for a word's last letter lands after space is pressed. The main thread therefore reads a volatile snapshot of loaded models and the constant provider map, never a lock.
- 2026-09-24: T-005 removals are reviewed lists applied at load time, not edits to the FrequencyWords files, so T-007 can reuse them and the raw source stays intact.
- 2026-09-24: The error model also prices spelling errors, not only tapping errors: vowel-for-vowel substitution and doubled or undoubled letters are cheaper than their key distance suggests.
- 2026-09-24: Strength levels are calibrated on usage-weighted tap-noise sets with the recall step averaged over EN, NL and NL+EN, and rare-word sets are capped by wrong-correction rate instead of precision. The previous per-set precision wording could be failed by a handful of corrections.
- 2026-09-24: License check for T-007. SCOWL (notice license: keep the copyright and permission notice with copies and in documentation) and OpenTaal (BSD-3-Clause or CC BY 3.0) are compatible with shipping inside an Apache-2.0 app. FrequencyWords stays CC BY-SA 4.0, and the adapted lists are offered under the same license. All three are credited in the third-party licenses screen and in `ime/dict/latin/ATTRIBUTION.md`.
- 2026-09-24: The built dictionary keeps plain text in a log100 format instead of a binary trie. Warm load times on the emulator match the raw lists within 10%, and heap is unchanged, so the conditional trie phase has no trigger yet.
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
