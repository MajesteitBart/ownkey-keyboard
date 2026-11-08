# Ownkey vs Gboard: results

Measured on a Samsung Galaxy S24 Ultra (Android 17), 2026-11-07 and 2026-11-08, with the fixed protocol in
`PROTOCOL.md`. All numbers are from the held-out test split (`corpus/test.jsonl`, 340 sentences: 150 en, 150 nl,
40 mixed). Nothing was tuned on it; tuning used the dev split and the offline simulator (`sim/`).

- Gboard: Dutch with English multilingual typing, personalised learning off for the field.
- Ownkey: beta build from branch `feat/prediction-vs-gboard` (APK sha256 in `results/final.apk.sha256`), Dutch
  with English as secondary language.

## The engine on main (0.11.0), this branch

Measured 2026-11-08 with the same protocol, Gboard runs unchanged. Three Ownkey builds:

- **0.11.0 before**: origin/main c9697eb5, unmodified (#17 noisy-channel scorer). One noise seed (s1).
- **First port**: the n-gram engine on 0.11.0 (`results/ngram-main.apk.sha256`).
- **Fixed port**: the first port with two integration bugs fixed (`results/ngram-main-fix1.apk.sha256`). A
  three-letter minimum from the noisy-channel scorer had been applied to the n-gram scorer, and `ChatShorthand`
  words ("ok", "jk") were never corrected. These were found by reading the first port's test-split errors. The
  fix was chosen and checked on the dev split only (`LatinPipelineReplayTest`); no parameter was tuned on test.

Autocorrect, words still wrong after typos (Ownkey / Gboard; target ≤ 0.75):

| Group | Gboard | 0.11.0 before (s1) | First port (s1+s2) | Fixed port (s1+s2) | Fixed port ratio, 95% CI |
|---|---|---|---|---|---|
| All | 2.01% | 4.02% (1.98×) | 1.20% (0.60×) | **0.86%** | **0.430**, 0.317 – 0.562 |
| English | 3.27% | 3.16% (1.00×) | 1.20% | 0.98% | 0.299, 0.196 – 0.430 |
| Dutch | 0.95% | 4.36% (4.23×) | 1.03% | 0.67% | 0.708, 0.419 – 1.125 |
| Mixed | 1.19% | 5.73% (4.00×) | 1.67% | 1.07% | 0.900, 0.333 – 2.500 |

Gboard columns pool seeds s1 and s2, except the 0.11.0 ratios, which use Gboard s1 (2.03% overall, 3.16% en,
1.03% nl, 1.43% mixed).

Prediction, keystrokes saved (Ownkey / Gboard; target ≥ 1.25). The first and fixed ports produced identical
taps on all 340 sentences.

| Group | Gboard | 0.11.0 before | Fixed port | Fixed port ratio, 95% CI |
|---|---|---|---|---|
| All | 41.52% | 45.08% (1.086×) | **55.28%** | **1.331**, 1.298 – 1.369 |
| English | 35.47% | 47.22% (1.331×) | 56.44% | 1.591, 1.533 – 1.652 |
| Dutch | 46.84% | 45.15% (0.964×) | 55.61% | 1.187, 1.155 – 1.224 |
| Mixed | 44.11% | 38.45% (0.872×) | 50.82% | 1.152, 1.114 – 1.191 |

Both pooled targets are met at the CI bound. Dutch and mixed sentences miss the per-language prediction target,
and their autocorrect intervals are too wide to conclude anything.

Files: `*_main-0.11.jsonl`, `*_ngram-main*.jsonl`, `*_ngram-fix1*.jsonl` in `results/`. The 0.11.0 build needed a
calibration change in `bench.py`: the redesigned space key has no accessibility node of its own, so its language
label is used instead.

The sections below are the earlier measurements of the engine on branch `feat/prediction-vs-gboard`.

## Pre-registered target

| Target | Needed | Result | 95% CI | Verdict |
|---|---|---|---|---|
| Prediction: KSR Ownkey / KSR Gboard (clean typist) | ≥ 1.25 | **1.335** | 1.302 – 1.372 | met, also at the CI bound |
| Autocorrect: WER Ownkey / WER Gboard (noisy typist) | ≤ 0.75 | **0.397** | 0.292 – 0.520 | met, also at the CI bound |

The WER row pools noise seeds s1 and s2 (6,020 words per keyboard). The intervals are sentence-level paired
bootstrap intervals (`compare.py`, `pool_seeds.py`).

## Prediction (clean typist, first three suggestion slots)

| Group | Gboard KSR | Ownkey KSR | Ratio | 95% CI | Next word right before the first letter, Gboard / Ownkey |
|---|---|---|---|---|---|
| All | 41.52% | 55.41% | 1.335 | 1.302 – 1.372 | 22.4% / 31.8% |
| English | 35.47% | 56.41% | 1.590 | 1.532 – 1.651 | 19.7% / 31.1% |
| Dutch | 46.84% | 55.99% | 1.195 | 1.163 – 1.233 | 24.8% / 33.8% |
| Mixed | 44.11% | 50.68% | 1.149 | 1.110 – 1.189 | 23.9% / 28.2% |

Gboard had 16 desyncs in this run and Ownkey had none. Most came from Gboard rewriting typed forms without an
apostrophe ("youre" to "you re", "whos" to "Whose"). The harness repairs the field without charging taps, so
desyncs do not lower Gboard's KSR.

## Autocorrect (noisy typist: Gaussian taps σ = 0.20 key pitch, 5% slips)

| Group | Gboard WER | Ownkey WER | Ratio | 95% CI |
|---|---|---|---|---|
| All | 2.01% | 0.80% | 0.397 | 0.292 – 0.520 |
| English | 3.27% | 0.94% | 0.287 | 0.190 – 0.414 |
| Dutch | 0.95% | 0.59% | 0.625 | 0.348 – 1.000 |
| Mixed | 1.19% | 0.95% | 0.800 | 0.273 – 2.250 |

Per seed: s1 0.361 (61 vs 22 word errors), s2 0.433 (60 vs 26).

| Seed | Typos fixed, Gboard / Ownkey | Correct words changed (false corrections), Gboard / Ownkey |
|---|---|---|
| s1 | 87.7% / 93.2% | 0.74% / 0.04% |
| s2 | 87.6% / 91.9% | 0.67% / 0.04% |

## Where the target is not met

- **Dutch prediction:** 1.195 against 1.25 needed. Ownkey would need about 58.5% KSR in Dutch; it has 56.0%.
- **Mixed-sentence autocorrect:** 0.80 against 0.75 needed, from 10 vs 8 word errors over 838 words. The interval
  is too wide to conclude anything either way.
- **Dutch autocorrect** meets the target on the point estimate (0.625), but its interval reaches 1.0.

Remaining Ownkey errors on the test split include real-word swaps ("balen" to "halen", "dertig" to "veertig"),
English words inside Dutch sentences ("Het" to "Hey", "van" to "can"), brand-like forms ("quick sync" to "Quik
Sync") and a few typos left as typed.

## Starting point

The Ownkey build before this work, run on the same protocol, had a noisy WER of 10.1% (Gboard 2.03%, ratio 4.98).
On the 140 sentences of its clean run (en 50, nl 50, mixed 40), its KSR was 32.1% against Gboard's 43.1% (ratio 0.745).

## Result files

| Run | File in `results/` |
|---|---|
| Gboard noisy s1 / s2 | `20261107T115045Z_gboard_noisy_test.jsonl`, `20261107T181333Z_gboard_noisy_test_s2.jsonl` |
| Gboard clean | `20261107T124833Z_gboard_clean_test.jsonl` |
| Ownkey final noisy s1 / s2 | `20261107T235524Z_ownkey_noisy_test_final.jsonl`, `20261108T005305Z_ownkey_noisy_test_final-s2.jsonl` |
| Ownkey final clean | `20261108T015047Z_ownkey_clean_test_final.jsonl` |
| Comparisons | `compare_noisy_test_final_s1.json`, `compare_noisy_test_final_s2.json`, `compare_clean_test_final.json` |

Reproduce the tables:

```bash
python3 -I compare.py --a results/20261107T124833Z_gboard_clean_test.jsonl --b results/20261108T015047Z_ownkey_clean_test_final.jsonl
python3 -I pool_seeds.py \
  results/20261107T115045Z_gboard_noisy_test.jsonl,results/20261107T235524Z_ownkey_noisy_test_final.jsonl \
  results/20261107T181333Z_gboard_noisy_test_s2.jsonl,results/20261108T005305Z_ownkey_noisy_test_final-s2.jsonl
```
