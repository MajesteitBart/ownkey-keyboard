# Keyboard prediction and autocorrect benchmark protocol

Status: fixed before any tuning (2026-11-07). Changing a metric definition after baselines exist requires
re-running every keyboard on the new definition and recording why.

## Goal

Ownkey must beat Gboard by 25% on both headline metrics, pooled over the held-out test split
(English, Dutch and Dutch-English mixed sentences), on the same phone with the same simulated touches.

| Headline metric | Definition | "25% better" means |
|---|---|---|
| Prediction: keystroke savings rate (KSR) | `1 - taps_used / taps_without_prediction`, clean typing, typist taps a visible suggestion when it equals the target word | `KSR_ownkey >= 1.25 * KSR_gboard` |
| Autocorrect: residual word error rate (WER) | word-level Levenshtein `(S + D + I) / N` between intended and final text after noisy typing with no suggestion taps | `WER_ownkey <= 0.75 * WER_gboard` |

Secondary metrics (reported, not gated): next-word top-3 hit rate (before the first letter), completion hit rate
after 1/2/3 letters, false-correction rate (correctly typed words that were changed), typo-fix rate (mistyped words
that ended correct), per-language splits, suggestion latency.

## Device and keyboard setup

- Phone: Samsung Galaxy S24 Ultra (SM-S928B), Android 17, 1440x3120, portrait.
- Gboard: Play Store build, defaults except one keyboard "Nederlands (Nederland), QWERTY" with multilingual typing
  English (US). Auto-correction, next-word suggestions and suggestion strip are on (defaults).
- Ownkey: build under test, QWERTY, Dutch primary with English available at the same time (no manual switching
  in the final comparison), default correction settings.
- Both keyboards: default height and theme, no learning across runs (see test field).

## Test field

The harness app shows one multi-line EditText with
`inputType = text | autoCorrect | capSentences | multiLine` and `imeOptions = IME_FLAG_NO_PERSONALIZED_LEARNING`,
so neither keyboard learns from benchmark text and every run starts from the same state. Between sentences the field
is cleared and input is restarted.

## Text normalisation

- Sentences contain letters, spaces and English apostrophes only (no digits, hyphens, URLs or emoji). Punctuation in
  the source text is dropped before typing.
- The typist types every letter in lower case and never presses shift. Words with apostrophes are typed without the
  apostrophe ("dont"), letters with diacritics are typed as their base letter ("een" for "één").
- Comparison is case-insensitive and folds apostrophes and diacritics on both sides, so "don't"/"dont" and
  "één"/"een" are equal. A keyboard is never penalised for adding or omitting an apostrophe or diacritic.

## Typist models

Taps are generated in key-relative units and mapped onto each keyboard's own measured key centres, so both
keyboards receive the same intent noise. Every random draw is seeded by `(run seed, sentence id, word index,
char index)`.

### Noisy typist (autocorrect metric)

- Tap position = key centre + `(N(0, 0.20 * pitch_x), N(0, 0.20 * pitch_y))`, where pitch is the centre-to-centre key
  distance on that row. Offsets are clamped to 0.45 pitch where the tap would otherwise leave the letter block: upward
  for the top row (suggestion strip), downward for the bottom letter row (space/symbol row), leftward for `z` (shift)
  and rightward for `m` (backspace). Amended 2026-11-07 before any baseline completed: unclamped taps drove the
  suggestion strip, shift and backspace, which measured the harness repair logic instead of correction quality.
- Cognitive errors: each word of length >= 4 independently gets, with probability 0.05, one of: adjacent
  transposition, omitted letter or doubled letter (uniform choice and position, seeded).
- After each word the typist taps space. Suggestions are never tapped. Backspace is never used.
- The intended text is scored against the final field text with the normalisation above.

### Clean typist (prediction metric)

- Taps at key centres, no cognitive errors.
- Before each tap (including before the first letter of a word) the typist reads the first three suggestion slots
  in display order (Gboard always shows exactly three; Ownkey can show a longer scrolling row, of which only the
  first three count, so a keyboard cannot win by showing more items). If a slot equals the target word (normalised)
  and tapping it saves at least one tap, it taps it. Picking a
  suggestion costs 1 tap and is assumed to also insert the following space. Otherwise it types the next letter, and
  taps space after the last letter.
- `taps_without_prediction = sum(len(word) + 1)`.
- If the field ever disagrees with the intended prefix after a word, the harness records a desync and repairs the
  field. Desyncs are reported per keyboard.

## Splits

- `dev`: used for tuning Ownkey (offline simulator and device spot checks).
- `test`: used only for headline numbers. Never used to build or tune models.
- Language models and dictionaries must not be trained on any benchmark sentence or on its source collection
  (Tatoeba is test-only).

## Reporting

Each run writes JSONL per sentence plus a summary JSON with the keyboard package, version, build commit, seed,
split, and all metrics. Headline comparisons use the same seed and split for both keyboards.
