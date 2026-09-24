---
name: Autocorrect engine rebuild
slug: autocorrect-engine
owner: ownkey-keyboard-team
status: active
created: 2026-09-24T11:09:21Z
updated: 2026-09-24T19:40:05Z
outcome: On the in-repo benchmark, autocorrect fixes at least 70% of EN, 65% of NL and 60% of mixed NL+EN touch typos with at least 98% precision on every typo set and no more than 0.5 false corrections per 1,000 correctly typed words, while suggestion latency stays under 30 ms p95 on device.
uncertainty: medium
probe_required: false
probe_status: skipped
operating_mode: feature
---

# Spec: Autocorrect engine rebuild

## Executive summary

Ownkey's autocorrect barely works. The 2026-09-24 baseline (`research/baseline-2026-09-24.md`) found that the current engine corrected 0 of 2,280 synthetic touch typos and 0 of 160 real-world misspellings in English and Dutch at default settings. At the most aggressive slider settings it corrected under 3%, and about a quarter of those picked the wrong word. An independent review re-ran the measurement and reproduced every number.

The earlier `typing-speed-core` work added recovery and control features on top of this engine: undo, backspace restore, never-correct, revert memory and app profiles. The planned tuning screen (T-013) never shipped. Those features are sound. The scoring underneath them is not. The confidence formula cannot reach its own threshold, the dictionaries treat common misspellings as correct and contain no English contractions, completions can never become corrections, and nothing in the engine knows which keys are next to each other.

The fix starts small. Phase 1 swaps the scoring for a standard noisy-channel posterior on the existing candidate indexes, fixes the commit path, removes known misspellings from the dictionaries, and makes the quick toggle work. The review measured that the scoring swap alone matches a full candidate-search rebuild on the benchmark sets, so a trie or binary dictionary only happens if memory or completion measurements call for it. Later phases add cleaned dictionaries, context and touch position, each behind a measured gate.

## Problem and users

Primary users:

- Bart and other fast typers who expect mainstream-keyboard autocorrect: type roughly, press space, get the intended word.
- EN/NL bilingual users who type both languages on one keyboard.
- Users who type names, product names and jargon that must not be "fixed".

What goes wrong today, from the baseline:

- Typos stay typos. `teh`, `becuase`, `thnaks`, `bedakt`, `vergaderign` all pass through unchanged at default settings.
- Some misspellings are dictionary words and can never be corrected: `mischien`, `eigelijk`, `untill`, `seperate`, `dont`.
- Pushing the correction prefs to their most aggressive values produces wrong corrections toward frequent words: `yout` becomes "you", `noet` becomes "niet". Most of those prefs have no settings UI, and there was no autocorrect on/off switch in settings either.
- English contractions cannot be produced at all.
- The suggestion bar shows the intended word first for only 60 to 81% of typos. A standard scorer reaches about 90 to 94% with the same candidates.

## Outcome and success metrics

All metrics come from the in-repo benchmark from T-001 unless noted, measured separately per language and for the NL+EN subtype.

Phase 1 gates were re-derived on the tap-noise sets during T-004, from the first scorer version (38% EN, 34% NL, 26% NL+EN at 97 to 98% precision) and the tuned version (see `decisions.md`). They are enforced as regression floors in `AutocorrectBenchmarkReportTest`. If precision and recall gates conflict, precision wins, and the recall gate moves to the phase that adds the missing signal (context in Phase 3 or touch in Phase 4).

| Metric | Baseline | Phase 1 gate | Final target |
| --- | --- | --- | --- |
| Tap-noise typos autocorrected right, usage-weighted, EN / NL / NL+EN | 0% / 0% / 0% | >= 65% / >= 60% / >= 55% | >= 75% / >= 72% / >= 65% |
| Precision (right / all corrections) on every typo set with at least 10 corrections, including vocabulary-uniform | never fires | >= 97% | >= 98% |
| Real-world misspellings autocorrected right, EN curated / NL curated plus extra | 0% / 0% | >= 60% / >= 75% | >= 70% / >= 85% |
| Legitimate out-of-dictionary words changed, set of 313 | 0 of 57 | <= 3 | <= 1 |
| False corrections on clean text, per 1,000 words | 0 (never fires) | <= 0.5 | <= 0.3 |
| Intended word is suggestion 1, tap-noise usage sets | 66-67% | >= 85% | >= 92% |
| Contraction forms producible (EN) | 0 of 14 | not gated | 14 of 14 (Phase 2) |
| Auto-commits applied from a candidate list computed for another input, on device | not measured | 0 | 0 |
| Suggestion latency p95, on device | not measured | < 50 ms | < 30 ms |
| Autocorrect undo rate during dogfooding, on device | not measured | <= 5% of applied | <= 3% of applied |

The undo rate comes from the existing `TypingSpeedMetrics` counters for applied and undone autocorrections. It is the only real-world precision signal available without logging typed text.

## User stories

- US-001: As a fast typer, I want an obvious typo fixed when I press space, so that I do not have to go back and edit.
- US-002: As a bilingual typer, I want Dutch and English typos fixed in the same message, so that I do not have to switch keyboards.
- US-003: As someone who types names and jargon, I want words I meant to type left alone, so that I can trust autocorrect.
- US-004: As any user, I want one clear strength setting and a working on/off toggle, so that I can control autocorrect without understanding confidence percentages.
- US-005: As an English typer, I want `dont` and `im` to become "don't" and "I'm", so that I do not have to switch to the symbols layer for apostrophes.

## Acceptance scenarios

- AC-001: Given the EN subtype, when the user types `becuase` and presses space, then the field contains "because ".
- AC-002: Given the NL+EN subtype, when the user types `mischien` and presses space, then the field contains "misschien ".
- AC-003: Given any subtype, when the user types `Voxtral` or `webhook` and presses space, then the word is unchanged.
- AC-004: Given an autocorrection just happened, when the user presses backspace once, then the original typed word is restored. This keeps existing T-012 behavior.
- AC-005: Given the user typed a word and pressed space before suggestions for the last letter finished, when autocorrect runs, then it uses a decision computed for the full typed word, never a list computed for an earlier prefix.
- AC-006: Given the EN subtype, when the user types `dont` and presses space, then the field contains "don't ".
- AC-007: Given autocorrect is switched off with the quick-action toggle, when the user types `teh` and presses space, then the word is unchanged and suggestions still show.
- AC-008: Given the user is typing `isn't` or `bart@example.com` character by character, when the apostrophe, `@` or a mid-token `.` is typed, then no autocorrection fires on the text typed so far.
- AC-009: Given the EN subtype, when the user types `becaus` and presses space, then the field contains "because ".

## Scope

### In scope

- Pure-Kotlin, JVM-testable scoring extracted from `LatinLanguageProvider`.
- In-repo benchmark with EN, NL and mixed datasets, run as a JVM test.
- Noisy-channel scoring with keyboard-geometry error costs, on the existing candidate indexes.
- Correct commit behavior: input-keyed decisions, right trigger characters.
- Curated dictionary removals now, a whitelist-based dictionary pipeline and contraction handling later.
- A static bigram model per language, combined with the existing personal n-gram store.
- Tap-position signal from the keyboard to the engine.
- One strength setting instead of the tuning sliders, and a working quick toggle.
- Keeping the Android spell-checker service, glide typing and non-EN/NL languages working on whatever the engine and dictionaries become.

### Out of scope

- LLM or other network calls in the keystroke path. Sentence-level fixes stay with the existing AI rewrite feature.
- Glide typing decoder changes, beyond a regression check when frequency scaling changes.
- New languages beyond EN and NL. Other languages keep the `data.json` fallback.
- Han and other non-Latin providers.

## Functional requirements

- FR-01: The engine scores candidates as log P(word | context) + log P(typed | word) and compares them against the literal input as an unknown-word option. Autocorrect decisions use the resulting posterior.
- FR-02: Error costs depend on key distance in the active layout, not on hard-coded QWERTY.
- FR-03: Completions and corrections are separate candidate sources that both enter the posterior. A completion pays an omission cost for each missing letter, so a dropped final letter (`becaus`) can autocorrect and longer completions cannot.
- FR-04: Candidate search starts at 3-letter inputs. Full-vocabulary search and edit distance 2 are added only if the benchmark shows a gain.
- FR-05: Autocorrect only fires when the top candidate's posterior clears the threshold for the active strength and app profile, and the input is not a dictionary, user-dictionary, personal-dictionary or never-correct word.
- FR-06: A capitalized unknown word in the middle of a sentence gets a higher unknown-word prior, because it is probably a name.
- FR-07: Autocorrect triggers only on space and on sentence punctuation (`. , ! ? ; :`) that ends a token. It never triggers on apostrophe, hyphen, digits, `@` or `/`, or inside tokens that contain `@` or look like a URL. Enter keeps today's behavior and does not autocorrect.
- FR-08: The commit path only applies a correction computed for the exact current word. Otherwise it decides synchronously, using in-memory data only.
- FR-09: English contractions and Dutch elisions (`z'n`, `m'n`) exist in the dictionaries. Unambiguous apostrophe-less forms autocorrect. Ambiguous forms (`ill`, `id`, `wont`, `cant`, `were`, `its`, `well`, `hell`, `shell`, `lets`) are suggestion-only until context scoring exists.
- FR-10: Existing recovery flows keep working: one-tap undo, backspace restore, revert memory (T-015), never-correct list.
- FR-11: One user-facing setting controls strength: Off, Gentle, Normal, Strong. App profiles shift the threshold from there.
- FR-12: `spell()` for `FlorisSpellCheckerService`, glide ranking through `getFrequencyForWord`, and the `data.json` fallback for other languages keep working after each phase.

## Non-functional requirements

- All autocorrect processing stays on device. No typed text leaves the phone for autocorrect.
- The benchmark must not contain private typed text. Datasets are synthetic, public or hand-written.
- Keyboard open and first-input latency must not regress more than 5% against the current release.
- Dictionary and index heap for EN+NL stays within 10% of the current release, measured in MB on the lowest-spec test device. The current release is the reference, since the level before the OOM fix in `ea7763fc` is the one that crashed.
- Tap positions are held only for the current word and never persisted.
- Data sources must be license-compatible with shipping inside an Apache-2.0 app.

## Assumptions

- Bart's main subtype is Dutch with English as a secondary language, or English only. Both are measured, and NL+EN has its own gate.
- The Phase 1 gates are reachable with scoring changes and curated dictionary removals, without a context model. Evidence so far is mixed. The untuned reference scorer reached 59% (EN) and 45% (NL) on synthetic typos with 1.2% or fewer wrong, but it misses the NL real-world gate (23%), the EN real-world gate (41%) and the out-of-dictionary gate (2 of 57 changed). A lower unknown-word prior reached 77% (EN) and 68% (NL) synthetic and 59% (EN) and 63% (NL) real-world, but precision on the vocabulary-uniform set fell to 91%. Calibration on non-circular data in T-001 and T-004 settles this.
- Bart's complaint matches the measured behavior: autocorrect does nothing at defaults, and the only reachable tuning (chat and e-mail aggressiveness) cannot change that.

## Needs clarification

- Which bigram corpus and APK size budget are acceptable for Phase 3. See `decisions.md`.
- Whether Normal or Gentle should be the default strength once Phase 1 passes its gate.
