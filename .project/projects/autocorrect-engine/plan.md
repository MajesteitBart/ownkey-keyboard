---
name: Autocorrect engine rebuild
status: active
lead: ownkey-keyboard-team
created: 2026-09-24T11:09:21Z
updated: 2026-09-25T14:23:05Z
linear_project_id:
risk_level: medium
spec_status_at_plan_time: planned
---

# Delivery plan: Autocorrect engine rebuild

## What changed after probe

No separate probe. The baseline measurement in `research/baseline-2026-09-24.md` served as one, and an independent review re-ran it. Two results reshaped this plan:

- Noisy-channel scoring on the current candidate indexes matched a full-vocabulary, distance-2 reference on every benchmark set. A trie rebuild is no longer part of the core plan.
- Log-scaling frequency inside the current confidence formula still fires 0% at defaults. The formula has to be replaced, not patched.

## Technical context

- Engine: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt` (1,367 lines) does dictionary loading, candidate generation, ranking, confidence, mixed-language weighting, personal boosts, e-mail suggestions and `spell()` in one class that needs an Android `Context`. None of the scoring is unit-testable today.
- Policy: `HighCertaintyAutocorrectPolicy.kt` and `AppSpecificAutocorrectProfilePolicy.kt` turn confidence into an auto-commit flag. `AppPrefs.Correction` holds the tuning prefs: minimum confidence, confidence gap and minimum length (hidden, no settings UI), chat and e-mail aggressiveness sliders, and the profile switch.
- Commit path: `KeyboardManager.handleSpace()`, hardware space, the media-mode branch and every non-alphabetic character call `nlpManager.getAutoCommitCandidate()`. That returns the first eligible candidate from the last finished async `suggest()` run on `Dispatchers.Default`. `handleEnter()` never autocorrects.
- Recovery: `AutocorrectUndoTracker`, backspace restore, `NeverCorrectWords` and revert memory already work and stay.
- Data: `app/src/main/assets/ime/dict/frequencywords/{en,nl}_50k.txt` are FrequencyWords 2018 OpenSubtitles lists (CC BY-SA 4.0). `data.json` is the old FlorisBoard list and the fallback for every other language.
- Other consumers of the same engine and data: `FlorisSpellCheckerService` calls `spell()`, and `StatisticalGlideTypingClassifier` ranks with `255 * getFrequencyForWord()`.

## Architecture decisions

1. Noisy-channel scoring replaces the hand-weighted confidence formula. For typed input `t` and candidate `w`: `score(w) = log P(w | context) + log P(t | w)`. The literal input competes as an unknown word with a tunable prior. Softmax over the candidates gives a posterior, and autocorrect fires when the best non-literal candidate clears the strength threshold. It is the textbook spelling-correction model and produces a number the policy can calibrate against.
2. Completions and corrections are separate candidate sources that both enter the posterior. A completion pays an omission cost per missing letter, which fixes `becaus` and keeps `wor` from turning into "working".
3. Error costs come from geometry. Phase 1 uses key-center distances from the active layout. Phase 4 replaces substitution cost with the likelihood of the actual tap position under each key.
4. Scoring moves into a pure-Kotlin engine (working name `AutocorrectEngine`) that takes dictionaries, layout geometry and context and returns ranked candidates with posteriors. `LatinLanguageProvider` becomes an adapter, and `spell()` uses the same engine. The benchmark runs against the code that ships.
5. The existing candidate indexes stay for now: the distance-1 delete index over the top 20,000 words and the prefix index. The review measured no gain from full-vocabulary or distance-2 search on the benchmark sets. A trie or binary dictionary becomes a conditional phase with explicit triggers (below).
6. Autocorrect decisions are keyed to their input. Candidates record the input they were computed for, and the commit path decides synchronously when the latest list does not match. The synchronous path uses in-memory snapshots only. Today, the user-dictionary check does a Room query and a system ContentResolver call, and the personal n-gram store loads from a file, which must not happen on the main thread per keystroke.
7. Autocorrect triggers only on space and token-ending sentence punctuation, never on apostrophe, hyphen, digits, `@` or `/`.
8. Dictionaries get two passes. Phase 1 applies a curated removal list to the current assets: known misspellings, backtick forms, OCR single letters. Phase 2 builds dictionaries from source lists with a script in `tools/dictionary-build/` and rebuilds contractions, which is when split fragments such as `don`, `didn` and `isn` can go. Thresholds get recalibrated after each dictionary change. That is cheap once the benchmark exists.
9. Settings collapse to one strength control (Off, Gentle, Normal, Strong) that maps to posterior thresholds. The sliders move to devtools. App profiles stay as threshold offsets.
10. Context comes in two layers: a static bigram model per language, interpolated with the existing `PersonalNgramStore`. No neural model in this project.

## Policy and contract checks

- [x] `.project` remains the execution source of truth
- [x] Probe decision is explicit (skipped, baseline measurement and review used instead)
- [x] Evidence gates are defined before handoff
- [x] External sync writes require dry-run or operator approval (not synced to Linear yet)

## Generated artifact map

- `spec.md`: written from the baseline measurement and code review on 2026-09-24, revised after the independent review.
- `plan.md`: this file.
- `research/baseline-2026-09-24.md` and `research/baseline-harness/`: baseline numbers, review results and the harness that produced them.
- `tasks/`: T-001 to T-008 cover Phases 0 to 2, T-009 to T-016 Phases 3 and 4, T-017 to T-020 Phase 5 (T-020 deferred), and T-021 the dogfood week. The conditional trie phase got no task: its trigger never fired (see `decisions.md`).
- `research/`: benchmark reports per task, from `benchmark-legacy.md` to `benchmark-phase5-t019.md`.

## Complexity exceptions

- None. The trie rebuild that needed one is now conditional.

## Workstream design

Single workstream, WS-A. T-002 and T-003 both edit `KeyboardManager.kt`, so they are sequenced even though each is small.

## Milestone strategy

### Phase 0: measure, and fix the toggle (T-001, T-002)

- T-001: move the harness into the repo as a JVM benchmark against extracted Kotlin scoring. Port the harness datasets verbatim, including the seed-42 generator, so the baseline reproduces. Add a tap-noise typo set that does not share the scorer's error model, a clean-text set, and an out-of-dictionary set of at least 300 words that includes Dutch compounds and chat shorthand. Re-derive the Phase 1 gates on the tap-noise set.
- T-002: wire the quick-action toggle to the existing `highCertaintyAutocorrectEnabled` pref. Independent of everything else and useful right away.

Gate: the JVM benchmark reproduces the harness baseline within 1 percentage point on the ported sets, and the Phase 1 gates are updated from the tap-noise results.

### Phase 1: quick relief (T-003, T-004, T-005)

- T-003: input-keyed commit path and correct trigger characters. It goes first, because the stale-candidate race only becomes harmful once corrections are eligible.
- T-004: noisy-channel posterior on the existing candidate indexes, 3-letter minimum, completions in the posterior, geometry costs, a name signal for capitalized unknown words, `spell()` on the same engine.
- T-005: curated removals from the current dictionary assets, so `mischien`, `untill` and `seperate` stop counting as correct.

Gate: every Phase 1 benchmark column in the spec metrics table, then one week of dogfooding on Bart's phone behind a devtools switch between the old and new scoring. The dogfood week must show 0 auto-commits from a mismatched candidate list, an undo rate of 5% or less, and suggestion latency under 50 ms p95.

### Phase 1b: strength setting (T-006)

Replace the sliders with Off, Gentle, Normal and Strong, calibrated on the benchmark with both precision and recall floors. The old values migrate.

### Phase 2: fix the dictionaries (T-007, T-008)

- T-007: build pipeline. Merge FrequencyWords counts with a validity whitelist (SCOWL for EN, OpenTaal for NL, after a license check), drop OCR artifacts, known misspellings and split fragments, rebuild contractions and elisions, and store log-scale frequencies. Glide ranking gets a regression check, because it reads these frequencies. Other languages keep working on `data.json`.
- T-008: apostrophe handling. Unambiguous forms autocorrect (`dont`, `im`, `youre`, `didnt`), ambiguous forms stay suggestion-only, and English standalone `i` becomes "I".

Gate: 14 of 14 contraction forms producible, 0 words from the curated misspelling list in the shipped assets, thresholds recalibrated, and every Phase 1 gate still met. The real-word error rate is re-measured, because a cleaner dictionary should lower it.

### Phase 3: context (T-009 to T-013)

Static bigram model per language from a license-compatible corpus, interpolated with personal n-grams. It enables `then`/`than`, `your`/`you` and ambiguous contractions, and improves next-word prediction and NL/EN language weighting. Real-word corrections start as suggestion-only. Starts with a short sourcing probe on corpus license and size (see `decisions.md`).

Gate: the spec's final real-world and mixed-language targets, plus a real-word error set where the correct word is the top suggestion at least 60% of the time.

Tasks: T-009 corpus probe (done: Tatoeba), T-010 context benchmark sets, T-011 bigram asset and loader, T-012 context in scoring, T-013 next-word predictions. The isolated-word tap sets cannot show a context gain, so the mixed-language recall target is measured on the context typo sets.

### Phase 4: touch model (T-014 to T-016)

Pass per-character tap positions for the composing word from the key input path to the engine, next to `EditorContent`. Substitution cost becomes the negative log likelihood of the tap under each key's Gaussian. Optionally learn a per-user offset on device. This is the signal that tells `yout` meaning "your" apart from "you".

Gate: the spec's final synthetic targets on the tap-noise set.

Tasks: T-014 tap positions from the keyboard, T-015 touch likelihood in the error model, T-016 candidate search for two-edit typos. Two Phase 3 targets moved here: Dutch context typos >= 72% (T-016) and Dutch real-word errors >= 60% (open; they mostly need the word after the error, which no phase provides yet).

### Phase 5: trust polish (T-017 to T-020)

- Learn out-of-dictionary words the user types and keeps (for example, 3 uses without undo) into the personal dictionary, together with the `personal-dictionary` project.
- Brief visual mark on a corrected word, tap to revert.
- Missed-space and extra-space fixes (`thisis`, `th eother`).

Tasks: T-017 learn words the user keeps, T-018 mark autocorrections in the text, T-019 fix missed spaces, T-020 real-word errors with the next word (deferred: it needs a correction of the previous word after the fact).

### Conditional: trie or binary dictionary

Only started when a measurement calls for it:

- The Phase 2 dictionaries plus indexes exceed the heap budget from the spec.
- Completion reach stays more than 10 points below the frequency oracle after the T-004 ranking change.
- The benchmark shows words outside the top 20,000 or edit distance 2 cases that the current indexes miss, at a volume that affects a gate.
- Dictionary load time blocks the keyboard-open latency budget.

## Rollout strategy

- T-002 ships as soon as it is done.
- Phase 1 ships with the new scoring on by default in the development branch, so dogfooding needs no setup; a devtools switch returns to the legacy scoring. The release default is decided after both the benchmark gate (passed) and the dogfood gate (open) pass.
- Each later phase follows the same pattern: benchmark gate, dogfood week, then default.
- Release notes describe the behavior change in plain words: "Autocorrect now fixes common typos when you press space. Press backspace to undo."

## Test strategy

- JVM benchmark (T-001) runs in `./gradlew :app:testDebugUnitTest` and prints the metrics table. Each passed gate becomes a floor that fails the build on regression.
- Unit tests for the posterior math, cost model, trigger characters, input-keyed decisions and the apostrophe table.
- Existing tests keep passing: `HighCertaintyAutocorrectPolicyTest`, `AppSpecificAutocorrectProfilePolicyTest`, `AutocorrectUndoTrackerTest`, `TypingSpeedMetricsTest`.
- Device checks on Bart's phone: the spec's acceptance scenarios, keyboard-open and typing latency through the existing `benchmark/` `TypingLatencyActivity`, the Android spell checker in a text field, glide typing on a short word list, and one non-EN/NL subtype.

## Rollback strategy

- The devtools switch returns to the legacy scoring without a release.
- Dictionary assets are versioned. The old `*_50k.txt` files stay until the Phase 2 format has shipped in a stable release.
- The strength-setting migration keeps the old pref keys readable for one release, so a rollback restores user choices.

## Remaining delivery risks

- Precision on rare target words is the binding constraint. The review measured 59% precision on the vocabulary-uniform set for the untuned reference, and 91% with a lower unknown-word prior. Phase 1 may need to stay conservative on rare words until context or touch data arrives.
- Calibration drift: thresholds tuned on benchmark data may be too aggressive for real typing. The dogfood undo-rate gate exists for this.
- Dutch compounds: Dutch forms new compound words freely (`vergaderplanning`), so many correct words are missing from any list. The unknown-word prior, the name signal and personal-vocabulary learning must keep those safe, and the out-of-dictionary set must include them.
- Main-thread work: the synchronous commit path must not reach Room, ContentResolver or file I/O. Measure on device, not only on the JVM.
- Licensing: SCOWL, OpenTaal and bigram corpora need a license check before they ship. FrequencyWords data is CC BY-SA 4.0 and already ships with attribution.
- Overlap with other projects: this replaces the confidence formula from `typing-speed-core` T-003 and makes `predictive-typing-quality-trust` T-001 and T-003 concrete. See `decisions.md`.
