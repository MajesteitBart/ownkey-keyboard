---
timestamp: 2026-09-25T14:30:06Z
status: in-progress
task: T-021
stream: WS-A
---

# Progress Update

## Completed
- PR #17 merged `main` (#16) into the branch; the only conflict was two icon imports in `ComputingEvaluator.kt`.
- Review fixes from the Codex and cubic reviews of 537e6c23. Autocorrect no longer changes a typo into a possibly offensive word while "Block possibly offensive words" is on (the default), and those words are left out of suggestions and next-word predictions. A suggestion batch only counts for the same language, text field, private mode and preceding text. Tap trails survive recompositions such as shift turning off. Key geometry counts in key pitches and reads the evaluated key codes. Popup characters in password fields are no longer kept. Undo no longer fires with the cursor inside the corrected word. The in-memory user dictionary respects entry locales and the enable switches. Smaller fixes: a Dutch single-letter fallback, a truncated bigram file is rejected, the interrobang triggers autocorrect, closing quotes no longer stick to words, d/dt/t alternatives.
- Dictionary: `overnieuw` is a word again and `verassingen` is removed; `build.py` pins OpenTaal and checks both sources by SHA-256 and reproduces the shipped lists. Six Tatoeba benchmark sentences were corrected and one non-sentence dropped.
- `:app:testDebugUnitTest`: 628 tests, 0 failures, 1 skipped (the opt-in tuning sweep), after both review rounds below. Isolated-word, real-world and clean-text benchmark numbers are unchanged; context typos fixed EN 77.9 -> 78.3%, NL 69.1 -> 69.6% after the dataset corrections, precision 99.1% or higher.
- Second round, from the Codex review of 565ebadd and the cubic reviews of 8d3282b2 and 565ebadd: a suggestion run is only reused on space when it is the latest run and the provider state (autocorrect settings, protected words) and the offensive-word setting are unchanged; cached suggestions and runs notice changes to the user and speech dictionaries, and a new input session refreshes the user dictionary snapshot. The offensive-word lists apply to every Latin keyboard and are checked word by word, so a missed-space split cannot bring a blocked word back; both lists gained missing inflections. `bigrams.py` also excludes the raw text of corrected benchmark sentences, and `build.py` only keeps verified downloads.

## In Progress
- Codex re-review of the new PR head.

## Blockers
- T-021: the dogfood week on Bart's phone and the release-default decision.
