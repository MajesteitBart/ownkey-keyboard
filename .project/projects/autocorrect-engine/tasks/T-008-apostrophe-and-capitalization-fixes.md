---
id: T-008
name: Apostrophe and capitalization fixes
status: done
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-24T20:52:36Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-007]
conflicts_with: []
parallel: false
priority: medium
estimate: S
story_id: US-005
acceptance_criteria_ids: [AC-006]
---

# Task: Apostrophe and capitalization fixes

## Description

Add a per-language table of apostrophe-less forms as an extra candidate source. Unambiguous forms autocorrect through the normal posterior gate. Ambiguous forms are suggestion-only until Phase 3 context exists. English standalone `i` becomes "I".

## Acceptance Criteria

- [x] Unambiguous EN forms autocorrect: `dont`, `im`, `youre`, `thats`, `didnt`, `doesnt`, `isnt`, `ive`, `wasnt`, `couldnt`, `wouldnt`, `shouldnt`, `havent`, `arent` (AC-006).
- [x] Ambiguous EN forms (`ill`, `id`, `wont`, `cant`, `were`, `its`, `well`, `hell`, `shell`, `lets`) only show the apostrophe form as a suggestion.
- [x] NL: `zn` and `mn` suggest "z'n" and "m'n". Auto-commit only if the benchmark shows no rise in clean-text false corrections. They auto-commit: clean-text false corrections are unchanged.
- [x] Standalone English `i` becomes "I" on space. Never inside other words, and never on NL-primary subtypes unless the surrounding words score as English.
- [x] The EN missing-apostrophe benchmark set reaches 14 of 14 in top-1 and at least 12 of 14 autocorrected.

## Traceability

- Story: US-005
- Acceptance criteria: AC-006

## Technical Notes

- A Dutch `i` typo for "is" or "in" must not be capitalized.
- The apostrophe itself must not trigger autocorrect (T-003), otherwise `isn` gets corrected before `isn't` is finished.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: `ApostropheForms` lists apostrophe-less forms that are not words themselves: 36 English (dont, im, youre, wasnt, ...) and 2 Dutch (zn, mn). A listed form typed in lowercase is a candidate and costs nothing in the channel model, because it is a spelling habit, not a slip; it may auto-commit below the 3-letter minimum. It still needs the Normal posterior (0.95), so Gentle leaves most of them alone. Ambiguous forms (ill, id, wont, cant, were, its, well, hell, shell, lets) are not listed and stay suggestions. English `i`, `i'm`, `i've`, `i'll` and `i'd` are shown with a capital I; a lowercase `i` becomes "I" on space when the words of the current sentence fit English at least as well as the other active languages, measured by word frequency (details in the review entry below). The Dutch list contains common English words from subtitles ("and" and "then"), so checking which dictionary knows a word was not enough.
- 2026-09-24: First try lowered the general apostrophe omission cost from 1.5 to 1.0 instead of using a table. `im` then reached the threshold, but `wasnt` still lost to "want" (0.935) and Gentle NL+EN fell to 98.86% precision (`aint` → "ain't" for a dropped letter of "saint"). Reverted in favor of the table the task describes.
- 2026-09-24: Dictionary fixes found by the table test and the device check: `heres` and `wheres` were rare SCOWL words that blocked "here's" and "where's", so `tools/dictionary-build` now folds them; `mn` (the state abbreviation) kept "m'n" from auto-committing on Dutch keyboards with English as second language, so it is on the English removal list. EN is now 40,818 words.
- 2026-09-24: Independent review (read-only, with probe tests) found six problems, all fixed with regression tests. (1) Listed forms fired on all-caps input and in the wrong language ("MN" became "M'N", "the mn" became "m'n"): a listed form now needs lowercase input, or a capital at the start of a sentence, and its language must fit the sentence. (2) "i.e." became "I.e.": a single letter before a period is no longer corrected (`AutocorrectTriggerPolicy`, with the trigger passed from `KeyboardManager`). (3) The suggestion cache ignored the words before the typed word, so an "I" decision could be reused in Dutch text: the cache key now includes that context. (4) Languages without their own dictionary borrow the English list, so Polish or Italian "i" was capitalized: "I" is off next to such a language (`LatinScoringLanguage.hasOwnDictionary`). (5) `i’m` lost its curly apostrophe: the typed word keeps it. (6) English-primary keyboards with Dutch left "i" lowercase after "lol" or a name: the primary language now keeps the lead unless a word clearly belongs to the other language, and chat abbreviations count for no language. The device check afterwards showed that a line break did not end the context; the language check now only looks at the current sentence.
- 2026-09-24: `ApostropheAndCasingTest` (14 tests), `AutocorrectTriggerPolicyTest` and the full `dev.patrickgold.florisboard.ime.*` suite pass: 515 tests, 0 failures. Benchmark (`research/benchmark-noisy-channel-t008.md`): EN missing apostrophes 14 of 14 autocorrected and 14 of 14 first (was 12 of 14 and 14 of 14); every other set unchanged, including clean text (0, 0.13, 0.13 and 0 per 1,000) and OOV (2 of 313). Strength calibration still passes: Gentle 99.1 to 99.6% precision, Normal 98.7 to 99.5%, Strong 97.0 to 97.9%.
- 2026-09-24: Emulator (API 35, debug build, soft-key taps in a Chrome textarea). English: `so im sure i dont know what i wasnt` → "So I'm sure I don't know what I wasn't". Backspace after "I" restored "i" and put `i` on the never-correct list, as designed; `in` stayed as typed. Dutch with English: `ik zag zn fiets en i hij zag mn boek` → "Ik zag z'n fiets en i hij zag m'n boek", and `and then i dont` → "and then I don't". One early run left a double space after "z'n"; four repeats of the same sequence, including right after a subtype switch and a fresh install, did not show it again. After the review fixes: `so i.e.` stays "i.e.", `and then i` followed by `wij zagen het ook en i` keeps the second "i" lowercase, and "Ik zag z'n fiets", Enter, `and then i dont know. het was i` gives "And then I don't know. Het was i".

- 2026-09-24: Task created from the missing-apostrophe benchmark set (0 of 14 today).
