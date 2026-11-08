# kbbench offline simulator

Replays the typists of `tools/kbbench/PROTOCOL.md` against the pure-Kotlin prediction engine in `lib/prediction`
(package `org.florisboard.lib.prediction`) instead of the phone. Use it to tune `EngineConfig` on the `dev` split
before device runs.

## Run

```sh
tools/kbbench/sim/run_sim.sh --touches both --out tools/kbbench/sim/results/dev.json
```

`run_sim.sh` wraps `./gradlew :lib:prediction:simulate --args="..."` under the shared Gradle lock
(`/tmp/ownkey-prediction-gradle.lock`, override with `OWNKEY_GRADLE_LOCK`). Paths are relative to the repository
root. Defaults: dev corpus, every dev tap seed (see below), `calib/ownkey.json`, Dutch primary plus English 50k
frequency lists, and the literal checks. The shipped models are the binary n-grams; simulate them with

```sh
tools/kbbench/sim/run_sim.sh --model nl=app/src/main/assets/ime/dict/ngram/nl.bin \
    --model en=app/src/main/assets/ime/dict/ngram/en.bin --touches both
```

The `.bin` reader (`org.florisboard.lib.prediction.ngram.NgramModel` with the `NgramWordModel` adapter) is part of
the engine module, so no extra Gradle flag is needed. It memory-maps the file like the phone maps the asset, so the
large models cost the simulator's heap only their offset indexes. The earlier 7 MB models are built by
`PROFILE=small tools/lm/build.sh` into `tools/lm/work/small/models/`.

| Option | Meaning |
|---|---|
| `--corpus`, `--calib` | Inputs; `--calib qwerty` uses an ideal grid instead of measured key centres |
| `--taps PATH[,PATH]` | Noisy tap plans, one per seed, repeatable; noisy metrics are pooled over all of them |
| `--model LANG=PATH` | Language model, repeatable, primary first; `.txt` = `word count` frequency list, `.bin` = n-gram |
| `--primary LANG` | Move that model to index 0 |
| `--loader EXT=CLASS` | Register a `ModelLoader` class (no-arg constructor) for another file extension |
| `--set KEY=VALUE` | `EngineConfig` override by field name, repeatable |
| `--touches on\|off\|both` | Pass touch points (key centre + recorded offset) or only key codes |
| `--mode clean\|noisy\|both` | Clean typist (KSR) and/or noisy typist (WER) |
| `--langs`, `--limit N` | Sentence languages, at most N sentences per language |
| `--details` | Per-sentence records (clean pick events and desyncs, noisy literal and output words per seed) |
| `--literal-checks PATH\|none` | Held-out keep/switch checks (default `literal_checks.jsonl`) |
| `--blocklist PATH` | Words never offered, repeatable (the phone blocks `app/src/main/assets/ime/dict/offensive/*.txt` by default) |
| `--confusion-sets PATH` | Confusion groups, one per line, plus the built-in Dutch d/dt/t endings: a typed word is never autocorrected, or revised while it stands as typed, to a word of its own group (the app's AC-011 rule) |
| `--keep-words PATH` | Words never autocorrected when typed exactly and never revised while they stand as typed (the app's word lists minus its removal lists, AC-011); repeatable. `--keep-words-scope current` applies them to the typed word only |
| `--grid KEY=V1,V2` | Grid search (cartesian product); `KEY` may also be `touches` |
| `--keep-floor X` | Grid rows whose literal-check keep rate is below X rank last (default 0.95) |
| `--threads N`, `--objective combined\|ksr\|wer` | Grid workers and ranking (combined = KSR - WER) |
| `--explain CTX\|INPUT[\|NEXT]` | Print the candidates (kind, log posterior, channel cost, each model's log probability in context; `*` marks a word that is not valid) and the autocorrect decision for `INPUT` after the comma-separated words `CTX`, with key codes; with `NEXT` also every reading the right-context revision scores (left, right and joint score, flags) and its decision; repeatable, nothing else runs |

## The app's configuration

The Ownkey app runs the engine as tuned here, with one rule on top (spec AC-011 of the autocorrect-engine project): a
typed word of one of its confusion groups (`ConfusionSets` in the app: "then"/"than", "its"/"it's", Dutch d/dt/t verb
endings) is never autocorrected to a word of its own group, and never revised to one while it stands as typed.
Simulate that with `--confusion-sets` and the app's blocklists:

```sh
tools/kbbench/sim/run_sim.sh --model nl=app/src/main/assets/ime/dict/ngram/nl.bin \
    --model en=app/src/main/assets/ime/dict/ngram/en.bin --touches both \
    --confusion-sets tools/kbbench/sim/confusion_sets.txt \
    --blocklist app/src/main/assets/ime/dict/offensive/en.txt --blocklist app/src/main/assets/ime/dict/offensive/nl.txt \
    --blocklist app/src/main/assets/ime/dict/offensive/ngram.txt
```

`confusion_sets.txt` mirrors the app's groups; keep it in step. The app's stricter devtools rule ("Keep every dictionary
word") corresponds to `--keep-words <app word lists> --set reviseTypedValidWords=false`; `--set correctValidWords=false`
is a stricter reading still, by the engine's own validity flags.

## The app pipeline on dev

The simulator measures the engine alone. `LatinPipelineReplayTest` (app unit tests) replays the same dev tap plans
through the app's own pipeline: auto-capitalised sentence starts, the trigger policy and `AutoCommitSelector`, the
provider's request and filters (`LatinAutocorrectPipeline`), the n-gram scorer with main's layers (confusion pairs,
chat shorthand, offensive words, missed-space splits), and the keyboard's revision path. It writes
`app/build/reports/autocorrect-benchmark/pipeline-replay.md` with the word error rate per language next to the engine
alone, and fails when the pipeline falls more than 0.1 percentage points behind. Only the dev split is read.

## Test split guard

The simulator refuses to run on test data unless `--allow-test` is given, and even then refuses `--grid` and
`--set`: a final test run uses the shipped configuration. Test data is recognised by the corpus `split` field, by
`-test-` in a corpus or taps sentence id, and by the `split` header of a taps file.

## Typists

- **Clean (KSR).** Before every letter (and before the first one) the typist reads the first three
  `displaySlots()`. If one folds equal to the target and picking saves a tap (`len - typed + 1 > 1`), it is picked
  for one tap; the next word's prediction follows at once (phantom space). Otherwise the typist types the next
  letter, and after the last letter taps space, which applies `autocorrect`. A correctly typed word that autocorrect
  changes counts as a desync. The intended word stays in the context.
- **Noisy (WER).** Each word's literal letters come from a taps file. The output is the autocorrect result or the
  literal, and becomes context for the next word. Reported: WER (word Levenshtein over folded words), false-correction
  rate (literal correct, output wrong), typo-fix rate (literal wrong, output right), and the literal WER for reference.
- **Right-context revision** (`PredictionEngine.revise`, on by default, `--set reviseEnabled=false` turns it off) runs
  as on the phone: after word *n* is committed with space, word *n − 1* may change, and the noisy typist keeps the
  revised word as context for the next words. The clean typist only counts revisions of the words it typed (each one
  is a false revision); it never revises a word it picked from the strip. Reported per language: `revisions`,
  `revisions_right` (a wrong word became right), `revisions_wrong` (a right word became wrong) for the noisy typist,
  and `revisions`/`false_revisions` for the clean one. With touches, a revision that only substitutes letters of a valid
  word typed exactly needs taps that lay off-centre towards the new letters (`reviseMaxTouchSubstitutionCost`), so the
  clean typist's centre taps never trigger one; with key codes there is no such evidence and the margin decides alone. A
short word that is in a vocabulary but not valid ("wss") is never revised (`--set reviseInvalidShort=true` allows it).
With `--details` the output also lists every revision the
  engine *proposed* with its margin, right-context gain, extra channel cost and committed-word class
  (`revision_proposals`), applied or not, for threshold sweeps without rerunning.

Latency is the wall time of each `suggest()` call after a warm-up (`--warmup N` sentences), single-threaded.

## Tap seeds

One seed gives about 300 typo words on dev, so two configs often differ by a single word. The default run therefore
pools five seeds: `tools/kbbench/taps_dev_ownkey.json` (seed `s1`) and `taps/taps_dev_ownkey_s2.json` to `_s5.json`,
exported from the dev split with

```sh
cd tools/kbbench && python3 -I export_taps.py --split dev --calib calib/ownkey.json --seed s2 --out sim/taps/taps_dev_ownkey_s2.json
```

The output lists WER per seed with its spread. A grid also evaluates the current configuration as a baseline and
marks a row `beats` only if its noisy WER is lower on every seed (paired by seed) and its clean KSR is not lower. The
clean KSR is one deterministic number shared by all seeds, so it is reported on its own (`ΔKSR`) and never counted per
seed; the `seeds` column gives the seeds with lower/higher WER. With `--objective ksr` (or without a noisy typist)
`beats` means a higher KSR.

## Literal checks

`literal_checks.jsonl` is a small hand-written list, not drawn from the benchmark corpora or Tatoeba. Each entry
types a word exactly after a short context and states the expected output:

- *keep* (`expect == input`): names, places, brands, chat slang, new compounds and neologisms that a lexicon may
  lack, and real words with an apostrophe or diacritic sibling (`wed`, `shell`, `een`);
- *switch*: apostrophe-less or diacritic-less spellings that should change (`dont` -> `don't`,
  `ideeen` -> `ideeën`).

The benchmark cannot see either kind: dev has under 1% words outside the lexicons, so it gives no signal against
over-correcting unknown words, and it folds apostrophes and diacritics. The checks always use key codes (centre
touches would make the literal look more certain than real taps do). Reported: keep rate, keep rate over words that
no lexicon contains, switch rate, and the failures. Grids rank rows below `--keep-floor` last.

Revision needs a next word, which the checks do not have. Each keep check is therefore also typed followed by every
word of a fixed list of frequent words of its language (`en`, `is`, `niet`, `ook`, `de`, `dat`, `morgen`, `zei` for nl;
`and`, `is`, `not`, `too`, `the`, `that`, `today`, `said` for en; chosen once, not per check), and the run reports the
keep rate over these pairs and every pair whose kept word a revision changed (`revision_keep_rate`,
`revision_false_n`, `revision_failures`). Many pairs are not natural text ("thanks ty the"), so this overstates the
risk; it is a guard, not a tuning target.

## Tuning

`grid.sh <stage>` runs prepared grids (`autocorrect`, `spelling`, `channel`, `display`) or
`grid.sh custom --grid ...`. Results go to `results/grid_<stage>.json` and a table sorted by the objective is
printed. Grids run on `dev` only. `results/` is ignored by git.

**Touch gain is an upper bound.** The synthetic noisy taps are unbiased Gaussians (0.20 pitch) around the intended
key centre, which is exactly what the touch model assumes, and the literal is always the nearest key. Real taps are
noisier and biased, so the WER gain from `--touches on` overstates what a phone will show. For that reason
`touchSigma` is not tuned here. It defaults to `keySigma` (0.45) until real tap logs give a fitted value.

## Plugging in another model

`.txt` (`UnigramListModel`) and `.bin` (`NgramWordModel`) are registered in `ModelLoaders`. For another format,
implement `org.florisboard.lib.prediction.WordLanguageModel` (ids, lower-case words, natural-log probabilities,
`topNext` for successors, `usesContext = false` for context-free models, optionally `displayForm` for the written
letter case) and a `org.florisboard.lib.prediction.sim.ModelLoader`. Then either call
`ModelLoaders.register("ext", loader)` or pass `--loader ext=<class name>` with the class on the simulate classpath.
