# Ownkey n-gram language models (tools/lm)

Offline pipeline that builds the static English and Dutch trigram models shipped in
`app/src/main/assets/ime/dict/ngram/{en,nl}.bin`, plus the Kotlin reader they are made for
(`lib/prediction/src/main/kotlin/org/florisboard/lib/prediction/ngram/`).

- Data and licences: [SOURCES.md](SOURCES.md)
- Binary format: [FORMAT.md](FORMAT.md) (version 2: version 1 plus the word validity bit sets)

## Rebuild

```sh
tools/lm/build.sh          # both languages, the shipped "large" profile (70 MB together), see "Profiles" below
tools/lm/build.sh nl       # one language
PROFILE=small tools/lm/build.sh   # the earlier 7 MB models (to tools/lm/work/small/models/), see "Profiles"
```

Needs `uv`, `curl` and network access. Python dependencies (`numpy`, `pyarrow`) go into `tools/lm/.venv`. All
intermediate data goes to `tools/lm/work/` (gitignored): downloads 4.6 GB, the large profile's prepared text and
counts about 12 GB in `tools/lm/work/large/` (`WORK_DIR`), the small profile's about 7 GB in `tools/lm/work/<lang>/`.
When it writes the app assets (the large profile's default `OUT_DIR`), the script also regenerates the Kotlin test
fixtures in `lib/prediction/src/test/resources/ngram/`. Run the reader tests with:

```sh
./gradlew :lib:prediction:test --tests 'org.florisboard.lib.prediction.ngram.*'
```

Evaluate a model (never on `test.jsonl`; the script refuses it):

```sh
tools/lm/.venv/bin/python tools/lm/eval.py --model app/src/main/assets/ime/dict/ngram/nl.bin \
    --data tools/kbbench/corpus/dev.jsonl --langs nl mix
tools/lm/.venv/bin/python tools/lm/eval.py --model nl.bin --model2 en.bin --weight 0.9 --data ... --langs mix
```

## Pipeline

| Stage | Module | What it does |
|---|---|---|
| prepare | `lmtool/prepare.py` | Streams the OPUS gzip and keeps every k-th line, or range-reads FineWeb parquet row groups. Normalises (`lmtool/textnorm.py`) to sentence segments of original-case tokens. |
| count | `lmtool/count.py` | Drops benchmark sentences (exact match), counts unigrams, casing and document dispersion, selects the vocabulary, repairs subtitle OCR `l`/`I` errors, counts bigrams and trigrams with numpy in 16 partitions (`--partitions`) keyed by the previous word. Dispersion blocks are 200 lines per file unless `--block-lines` says otherwise; vocabularies over 262,143 words get wider ids (`counts.json` records the layout). |
| build | `lmtool/kn.py`, `lmtool/binfmt.py` | Interpolated modified Kneser-Ney, pruning, backoff renormalisation, quantisation, binary export. |
| validity | `lmtool/validity.py` | Marks each vocabulary word as valid (curated word list, capitalised, or frequent) and as listed (a lower-case word of the curated list), stored in the `VALD` section. |
| eval | `eval.py` | Next-word top-1/top-3, perplexity, simulated keystroke savings, completion hit rates. |
| fixtures | `make_fixture.py` | Tiny models and Python-reference answers for the Kotlin tests. |

### Tokenisation (must match the keyboard)

Lower case. A word is a run of Unicode letters with internal apostrophes (`’` becomes `'`). Edge apostrophes are
stripped (`'t` becomes `t`). Runs that contain a digit or underscore are dropped, and hyphens split words. Every
sentence segment starts with the `<s>` context token. Segments start at the line start, after `!` and `?`, after a
`.` that does not end a known abbreviation or an initial, and after an ellipsis or dialog dash followed by an
upper-case letter. Subtitle noise is removed first: HTML and ASS tags, `[...]` and `(...)`, speaker labels, leading
dialog dashes, credit lines, and lines with music notes.

### Vocabulary and casing

- Up to 250,000 (nl) and 150,000 (en) words with count >= 5 that occur in >= 3 dispersion blocks: 1000 raw lines of
  the every-line Dutch subtitles, 200 prepared lines elsewhere (about one film or a few web pages). The block rule
  removes film-specific character names and one-off typos. Result: nl 250,000 words (effective minimum count 22),
  en 150,000 words (effective minimum count 18). The small profile: at most 150,000 words, en 142,258 words, nl
  150,000 (effective minimum count 9).
- OCR repair: a token that starts with `l` is mapped to its `i` form when that form is at least 10 times more
  frequent and the token is mostly sentence-initial or an English pronoun form (`lt's -> it's`, `l'm -> i'm`,
  `lk -> ik`, `lemand -> iemand`). nl 274 repairs, en 362 (small profile: nl 88, en 107).
- Casing flag: a word is flagged when at least 60% of at least 3 mid-sentence occurrences (outside all-caps lines)
  are capitalised. The dominant form is stored (`Amsterdam`, `iPhone`, `FBI`, `I'm`).

### Word validity

The vocabulary is chosen by frequency, so it also holds subtitle typos ("mischien"), OCR debris, truncations and
joined words ("ljust", "ofthis"). `lmtool/validity.py` marks a word *valid* when (after lower-casing and straightening
apostrophes) it is in the curated word list of the language (OpenTaal for Dutch, SCOWL/ESDB size 70 US+GB for
English; SOURCES.md), when its casing flag is set (names, places, brands, acronyms), or when it occurs at least
1 time per million training tokens (`--valid-min-ppm 1`: 800 occurrences for nl, 563 for en, spread over at least
three blocks by the vocabulary rule). A word is *listed* when the list carries it in lower case (a dictionary word,
not a name or an acronym).

Why 1 per million (studied on the small profile's data): below it, the words that no list knows are mostly debris
(samples of the 0.2-0.5 ppm band: "geeerd",
"zoowel", "centimer", "ljust", "offthe"), between 1 and 2 ppm mostly real chat and loan words ("nope", "webinar",
"jaja", "ipv", "roadmap"), though some debris is frequent ("mischien" at 2-5 ppm, "ther" at 1-2 ppm). Chat
abbreviations are rarer still in subtitles and web text ("gwn" 0.08, "idd" 0.75, "wss" 0.09, "idk" 0.14 ppm), so no
frequency threshold keeps them apart from debris; the engine therefore never treats a word that is not valid as
unknown, it only lowers the margin needed to replace it (`EngineConfig.deltaInvalid`, tuned on kbbench dev with the
literal checks). On dev, the thresholds 0.5, 1 and 2 ppm gave the same WER with corrections restricted to valid
targets except that 2 ppm lost "roadmap"-like targets (+0.02 pp); 1 ppm is the middle of that range.

| Model | vocabulary | in list | listed (lower case) | capitalised | frequent only | valid | not valid (share of tokens) |
|---|---|---|---|---|---|---|---|
| nl | 249,999 | 145,546 | 128,663 | 84,624 | 813 | 210,295 | 39,704 (0.39%) |
| en | 149,999 | 78,878 | 65,062 | 72,323 | 428 | 133,467 | 16,532 (0.19%) |
| nl, small profile | 149,999 | 100,904 | 89,493 | 47,041 | 716 | 134,415 | 15,584 (0.27%) |
| en, small profile | 142,257 | 77,731 | 64,253 | 64,592 | 434 | 124,828 | 17,429 (0.20%) |

The larger nl vocabulary adds mostly rare compounds, of which a larger share no list knows, so the not-valid share
grew; the engine's validity margins were re-checked on dev with these models (`EngineConfig`).

### Smoothing: why Kneser-Ney

The keyboard needs two kinds of query: top-k next words, and `log P(w | context)` for any vocabulary word to rank
completions and autocorrect candidates. The second needs a normalised distribution. With stupid backoff, scores
are not probabilities, and they cannot be compared across contexts or mixed between languages (nl + en for mixed
typing). Interpolated modified Kneser-Ney (three discounts per order, from count-of-counts) gives well-calibrated
lower orders through continuation counts. That matters here because most queries back off: a word typed after a
rare context is ranked by its bigram or unigram continuation probability. After pruning, each context gets an
ARPA-style renormalised backoff weight, so the stored model is a proper backoff model.

### Pruning

Per context, successors are ranked by `c(h,w) * (log P(w|h) - log(g(h) * P_backoff(w|h')))` (Seymore-Rosenfeld
weighted difference). Trigrams also need count >= 2. Entries below a threshold are dropped. Then
`bow(h) = (1 - sum kept P) / (1 - sum kept P_backoff)`.

Tuning on `dev.jsonl` showed that fixed per-context caps (64 bigram / 24 trigram successors) were the main loss.
Frequent contexts such as `de`, `ik`, `<s>` and `the` need long lists for prefix completion. Shipped settings
(large profile): no practical bigram cap (5000), trigram cap 255, thresholds bigram/trigram nl 16/40 and en 12/30
(small profile: nl 25/60, en 20/50). On the small profile's data:

| nl, same data | size | top-3 | KSR | perplexity |
|---|---|---|---|---|
| near-unpruned (caps 64/24, thresholds 2/4) | 36.3 MB | 33.8% | 52.1% | 210 |
| caps 64/24, thresholds 20/40 | 6.8 MB | 32.9% | 51.7% | 227 |
| caps 160/48, thresholds 20/40 | 7.3 MB | 33.2% | 53.1% | 191 |
| uncapped, thresholds 25/60 (small profile) | 7.0 MB | 33.1% | 55.1% | 149 |

Probability codes: 6 bits (3-byte entries, 18-bit ids) performed the same as 12 bits (4-byte entries) on dev
(nl KSR 51.7% for both, perplexity 227 vs 227) at 33% more size, so 3-byte entries are shipped.

## Final models (dev.jsonl)

The shipped models are the large profile's:

| Model | size | vocab | bigrams | trigram contexts | trigrams | top-1 | top-3 | start top-3 | KSR | perplexity |
|---|---|---|---|---|---|---|---|---|---|---|
| en on en | 31.6 MB | 150,000 | 3,758,700 | 1,169,871 | 4,045,081 | 19.8% | 33.5% | 17.3% | 56.4% | 123 |
| nl on nl | 38.6 MB | 250,000 | 4,510,443 | 1,352,744 | 4,687,051 | 21.0% | 33.5% | 37.3% | 56.9% | 124 |
| nl on mix | | | | | | 18.7% | 29.1% | 32.5% | 50.9% | 367 |
| 0.9 nl + 0.1 en on mix | | | | | | 18.7% | 29.1% | 32.5% | 51.0% | 366 |
| small profile: en on en | 6.9 MB | 142,258 | 866,187 | 124,961 | 485,236 | 19.0% | 31.7% | 22.0% | 54.9% | 149 |
| small profile: nl on nl | 7.0 MB | 150,000 | 810,222 | 123,907 | 504,196 | 20.2% | 33.1% | 37.3% | 55.1% | 149 |
| small profile: nl on mix | | | | | | 18.2% | 27.3% | 32.5% | 48.6% | 414 |
| small profile: 0.9 nl + 0.1 en on mix | | | | | | 18.2% | 27.3% | 32.5% | 49.3% | 442 |

KSR here counts n-gram suggestions only (three slots, picking costs one tap including the space). The reader works
on the file in place (see "Reader cost" below): on the desktop JVM a memory-mapped large model loads in 60-95 ms,
keeps 3.4 MB (nl) and 2.2 MB (en) of offset indexes on the heap, and a one-letter `topCompletions` takes 10-45 µs.

## Profiles

`tools/lm/build.sh` (`PROFILE=large`, the default) builds the shipped pair, sized for an APK budget of about 75 MB
for both files: prepared text and counts go to `tools/lm/work/large/` (`WORK_DIR`), the models to
`app/src/main/assets/ime/dict/ngram/` (`OUT_DIR`), and the Kotlin fixtures are regenerated. From the downloads it
takes about 45 minutes (preparation and web fetches of about 1 GB take most of it; counting and building both
languages take 11 minutes), keeps about 12 GB on disk, needs about 10 GB more for transient count chunks while nl is
counted, and peaks at 2.7 GB per process. `PROFILE=small` builds the earlier 7 MB models (shipped before the large
ones; about 25 minutes from scratch, 2-3 minutes once prepared) from `tools/lm/work/<lang>/` into
`tools/lm/work/small/models/`. The fixtures are regenerated only when `OUT_DIR` is the assets directory.

| | small | large (shipped) |
|---|---|---|
| nl data | every 5th subtitle line + 30M web tokens (149.5M) | every subtitle line (596M) + 210M web tokens (806.5M) |
| en data | every 25th subtitle line + 30M web tokens (125.3M) | every 5th subtitle line (476M) + 90M web tokens (566.0M) |
| nl vocabulary | 150,000 (effective min count 9) | 250,000 (min count 5, min-df 3; effective min count 22) |
| en vocabulary | 142,258 (min count 5) | 150,000 (effective min count 18) |
| dispersion block | 200 prepared lines | 1000 lines for the every-line nl subtitles (still about one film), 200 otherwise |
| thresholds bigram/trigram | nl 25/60, en 20/50 | nl 16/40, en 12/30 (caps 5000/5000/255 unchanged) |
| count partitions | 16 | 32 (lower peak memory; the result is the same) |

Results on `dev.jsonl` (n-gram only, three slots; heap = `NgramModel.heapBytes`, the reader's own indexes, with the
file memory-mapped; load = desktop JVM, memory-mapped, first load in the test JVM / after warm-up; the earlier reader,
which copied the file into arrays, needed 62.0 and 50.5 MB of heap and 155 / 70 and 145 / 60 ms):

| Model | size | heap | load | top-3 | KSR | perplexity | OOV |
|---|---|---|---|---|---|---|---|
| nl large on nl | 38.6 MB | 3.4 MB | 97 / 76 ms | 33.5% | 56.9% (+1.8 pp) | 124 | 0.00% |
| nl large on mix | | | | 29.1% | 50.9% (+2.3 pp) | 367 | 0.25% |
| en large on en | 31.6 MB | 2.2 MB | 68 / 64 ms | 33.5% | 56.4% (+1.4 pp) | 123 | 0.00% |
| 0.9 nl + 0.1 en large on mix | | | | 29.1% | 51.0% (+1.7 pp) | 366 | 0.00% |

Small models for comparison: 7 MB, 1.9 MB heap (11.6 MB with the copying reader), 7-8 ms after warm-up. A one-letter
`topCompletions` takes 10-45 µs. Gains are against the small models; paired bootstrap over dev sentences (95%
intervals): nl +1.2 to +2.4 pp, mix
+1.3 to +3.3 pp, en +0.9 to +2.0 pp. The dev subsets are small (1272 nl, 395 mix, 1360 en words): differences below
about 0.4 pp are noise.

What was tried (all on dev, sizes 7-55 MB; the per-run records are not kept in the repository):

- Pruning alone (default counts) gives little: nl 55.1% at 7 MB, 55.7% at 11 MB, 55.8% at 22 MB, 56.2% at 40 MB.
- More subtitles help nl by about 0.5 pp at the same size, but with only 30M web tokens the mixed Dutch-English
  sentences get worse (48.3% vs 49.3% at 40 MB): the web share falls from 20% to 5%. Web samples of 120M, 210M and
  390M tokens give mix 49.6%, 50.9% and 50.9% at about 40 MB; 390M costs nl 0.3 pp, so 210M (26% web) is used.
- Vocabulary: nl 150k and 250k give the same KSR at the same size, but 250k lowers the mix OOV on the same data
  (0.76% to 0.51%; 0.25% with the 210M web sample) and adds mostly real compounds ("diplomauitreiking",
  "dakbeplating"). 400k (19-bit ids, so 5-bit probability codes, or 4-byte entries) gives the same KSR for 3.5 MB
  (13 MB) more and 95k words that are not valid (250k: 40k). en 200k gives the same as 150k.
- Size ladder of the large data, nl (KSR nl / mix): 15.7 MB 56.0/50.1, 19.9 MB 56.4/50.0, 26.9 MB 56.6/50.6,
  38.6 MB 56.9/50.9, 43.6 MB 57.0/50.9, 50.2 MB 57.1/50.7 (heap 80 MB). en: 11.7 MB 55.6, 19.9 MB 56.1, 27.4 MB 56.2,
  31.6 MB 56.4, 37.7 MB 56.3.
- Bigram/trigram threshold ratio (10/60, 12/50, 16/40 at 37-39 MB): the same within noise. Bigram caps of 20,000 or
  60,000 instead of 5000 lower perplexity (124 to 122) but not KSR (+0.2 nl, -0.2 to -0.3 mix), and make a
  one-letter completion after a frequent word 3-7 times slower, so the cap stays 5000.

Reader cost: `NgramModel` reads the file in place (zero copy). Every section stays in the `ByteBuffer` and is read with
absolute little-endian gets; the heap only holds what queries need in constant time and the file stores as varints or
length prefixes: the byte offset of every word, the first bigram entry and the first trigram context of every word
(three int tables of 4 bytes per word), the first trigram entry of every 16th context, a rank bit set and a hash table
over the words with a folded form, the codebooks and the per-block unigram maxima (`NgramModel.heapBytes`: nl 3.4 MB,
en 2.2 MB). Because a frequent context has up to 5,000 bigram successors, `logProb` hashes the successor lists of a
context once it is asked twice in a row on a thread, so scoring a few hundred candidates in one context does not scan
the lists each time (same values; the table is at most 128 KB per thread and model). The app stores the assets
uncompressed (`androidResources.noCompress` in `app/build.gradle.kts`) and memory-maps them
(`LatinPredictionEngines.mapAsset`), so the 70 MB of model files are file-backed pages that the system loads on use
and may drop again, not Java heap. If mapping fails, `load(InputStream)` reads exactly the file (its size is in the
header) into one heap array, without a second copy. Desktop JVM, both large models plus their lexicons and the engine
after 9,332 `suggest()` calls on dev text: 25 MB retained heap (the copying reader: 139 MB, of which the models 118 MB);
`suggest()` p50 0.9 ms, p95 4.9 ms (copying reader: 3.2 / 5.7 ms). Stored, the pair adds 70 MB to the APK (deflated
it would be about 60 MB: nl 32.6, en 27.1).

## Integration notes (Kotlin reader)

```kotlin
val model = NgramModel.load(mappedBuffer)   // off the main thread; or load(File) (maps it), or load(InputStream) (copies)
val ctx = model.contextOf(textBeforeCurrentWord)        // NgramContext(prev1Id, prev2Id), handles <s>
model.topNext(ctx.prev1Id, ctx.prev2Id, limit = 3)      // List<NgramCandidate(wordId, logProb)>, natural log
model.topCompletions("dont", ctx.prev1Id, ctx.prev2Id, 3)  // folded prefix: matches "don't"; "een" matches "één"
model.logProb(model.wordId("afspraak"), ctx.prev1Id, ctx.prev2Id)
model.casedForm(id)                                     // "Amsterdam", "iPhone", "I'm" when isCapitalized(id)
model.isValid(id)                                       // a real word (list, name or frequent), not subtitle debris
```

- Ids: `0` = `<s>` (`NgramModel.BOS_ID`); unknown words are `-1` (`UNKNOWN_ID`); `logProb` of an unknown word is
  negative infinity. `wordId` expects a model token: call `NgramModel.normalizeToken(raw)` first (lower case,
  straight apostrophe, edge apostrophes stripped).
- Context: `prev1Id = BOS_ID` at sentence start (then `prev2Id` is ignored); `prev2Id = BOS_ID` for the second word.
  An unknown `prev1Id` falls back to unigram probabilities.
- Prefixes are folded (diacritics and apostrophes ignored, case-insensitive). The typist in `PROTOCOL.md` types
  base letters without apostrophes, so this is the matching the benchmark rewards.
- Thread-safe: all state is immutable after `load` and the buffer is only read with absolute gets. Queries allocate
  only small per-call buffers (plus the per-thread context table of `logProb`). The model keeps its buffer, and so a
  mapping, alive; do not modify the buffer afterwards.
- Store the asset uncompressed (`androidResources { noCompress += ".bin" }`) and map it:
  `assets.openFd(path)` -> `FileInputStream(fd.fileDescriptor).channel.map(READ_ONLY, fd.startOffset,
  fd.declaredLength)` (`LatinPredictionEngines.mapAsset`). A compressed asset cannot be opened as a file descriptor;
  then `load(InputStream)` copies the file once into the heap (38.6 MB for nl).
- The vocabulary is frequency-based and still contains subtitle spellings ("dont", "gonna", some film names) and
  debris. `model.isValid(id)` tells real words apart (see "Word validity"); `model.isListed(id)` marks dictionary
  words. Version 1 files report every word as valid and listed.
- Mixed Dutch-English typing: combine both models with a linear mixture of probabilities, weighted by language
  confidence. A static 0.9 nl / 0.1 en mixture beat Dutch-only on the mixed dev subset; 0.5/0.5 hurt.
