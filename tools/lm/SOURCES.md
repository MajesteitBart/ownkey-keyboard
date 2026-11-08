# Training data sources

The n-gram models in `app/src/main/assets/ime/dict/ngram/` are built only from the sources below by
`tools/lm/build.sh`. Benchmark data (`tools/kbbench/corpus/`, including Tatoeba) is never used for training:
before counting, every training line whose normalised token sequence (or one of its sentences) exactly equals a
`dev.jsonl` or `test.jsonl` sentence is dropped (compared after folding diacritics and apostrophes).
`test.jsonl` is read only for that filter.

The shipped models are the large profile's (`tools/lm/build.sh`, `PROFILE=large`, the default; README.md,
"Profiles"):

| Language | Source | Part used | Tokens after normalisation |
|---|---|---|---|
| nl | OPUS OpenSubtitles v2018 mono `nl.txt.gz` | every line (104,652,076 lines) | 596,027,352 |
| nl | FineWeb-2 `nld_Latn` train shard `000_00000.parquet` | row groups 0, 37, 74, ... (the first 72; 72,000 documents), then row groups `6 + 37k`, `12 + 37k`, `18 + 37k` and `24 + 37k`, each series up to 45M tokens (109 + 108 + 108 + 107 row groups; 504,000 documents in total) | 210,486,429 |
| en | OPUS OpenSubtitles v2018 mono `en.txt.gz` | every 5th line (88,290,090 of ~441.5M lines) | 476,213,589 |
| en | FineWeb `sample/10BT` shard `000_00000.parquet` | row groups 0, 9, 18, ... (the first 60; 59,981 documents), then row groups 3, 12, 21, ... (all 117; 176,932 documents in total) | 89,751,592 |

Totals counted after exclusion filtering: nl 806.5M tokens (359 lines dropped as benchmark matches), en 566.0M
tokens (126 lines dropped). The web samples fetch about 650 MB (nl) and 350 MB (en) with HTTP range requests. Exact
numbers per build are in the model `META` section and in `tools/lm/work/large/<lang>/counts/counts.json`.

### Small profile (`PROFILE=small`, shipped before the large models)

Same corpora, files and licences as above, only smaller parts of them. No other source is used by either profile.

| Language | Source | Part used | Tokens after normalisation |
|---|---|---|---|
| nl | OPUS OpenSubtitles v2018 mono `nl.txt.gz` | every 5th line (20,930,416 of ~104.7M lines) | 119,193,257 |
| nl | FineWeb-2 `nld_Latn` train shard `000_00000.parquet` | row groups 0, 37, 74, ... (72 of 4,004; 72,000 documents) | 30,354,111 |
| en | OPUS OpenSubtitles v2018 mono `en.txt.gz` | every 25th line (17,658,018 of ~441.5M lines) | 95,226,807 |
| en | FineWeb `sample/10BT` shard `000_00000.parquet` | row groups 0, 9, 18, ... (60 of 1,049; 59,981 documents) | 30,106,013 |

Totals counted after exclusion filtering: nl 149.5M tokens (61 lines dropped as benchmark matches),
en 125.3M tokens (26 lines dropped); counts in `tools/lm/work/<lang>/counts/counts.json`.

## Downloads

- OpenSubtitles v2018, monolingual plain text (one sentence per line):
  - `https://object.pouta.csc.fi/OPUS-OpenSubtitles/v2018/mono/nl.txt.gz`: 990,401,894 bytes,
    ETag `69f0dd13d2ade3e3fa643451c105fab4`, last modified 2018-11-17
  - `https://object.pouta.csc.fi/OPUS-OpenSubtitles/v2018/mono/en.txt.gz`: 3,663,376,519 bytes,
    ETag `77fa5bae9f20913e0ebc502e206b8e94`, last modified 2018-11-17
- FineWeb-2 (`HuggingFaceFW/fineweb-2`, revision `af9c13333eb981300149d5ca60a8e9d659b276b9`):
  `data/nld_Latn/train/000_00000.parquet` (4,848,392,885 bytes). Only the `text` column of the selected row groups
  is fetched with HTTP range requests (about 650 MB for the large profile, 98 MB for the small one).
- FineWeb (`HuggingFaceFW/fineweb`, revision `9bb295ddab0e05d785b879661af7260fed5140fc`):
  `sample/10BT/000_00000.parquet` (2,147,292,183 bytes), `text` column of the selected row groups (about 350 MB
  for the large profile, 118 MB for the small one).

## Licences and attribution

- **OpenSubtitles (OPUS)**: subtitles from http://www.opensubtitles.org/, collected by OPUS
  (https://opus.nlpl.eu/OpenSubtitles/). OPUS states no separate licence for this corpus. It asks users to link to
  http://www.opensubtitles.org/ and to cite: P. Lison and J. Tiedemann, 2016, *OpenSubtitles2016: Extracting
  Large Parallel Corpora from Movie and TV Subtitles*, LREC 2016. Ownkey's existing frequency lists
  (hermitdave FrequencyWords, CC BY-SA 4.0) come from the same corpus.
- **FineWeb-2** and **FineWeb**: Open Data Commons Attribution License (ODC-By) v1.0. Use is also subject to the
  Common Crawl terms of use. Citation: G. Penedo et al., *The FineWeb Datasets* (2024) and *FineWeb2* (2025),
  Hugging Face.

The shipped models contain only aggregated, pruned and quantised n-gram statistics plus a word list. They contain
no running text.

## Word lists (validity flags)

The models' `VALD` section (format version 2, see FORMAT.md) marks which vocabulary words are really words. It is
derived from these curated lists; only one bit per vocabulary word is stored, no list text. Downloads go to
`tools/lm/work/wordlists/` (gitignored) and are fetched by `build.sh` when missing.

| Language | List | Version | Licence | Entries -> model tokens |
|---|---|---|---|---|
| nl | OpenTaal Dutch word list, `wordlist.txt` from https://github.com/OpenTaal/opentaal-wordlist | 2.20.23 (2023-03-10), commit `b250510dda431785f962019167d1415198ff3905` | Revised BSD (3-clause) and/or CC BY 3.0, at the user's choice; Ownkey uses it under the Revised BSD licence | 413,937 -> 398,224 |
| en | SCOWL / English Speller Database (ESDB), https://github.com/en-wl/wordlist, generated at https://app.aspell.net/create with size 70 (large), spellings US + GB (-ise and -ize), variant level 1, diacritics both, special "hacker" (no Roman numerals); contractions and their apostrophe forms are part of the list (`don't`, `y'all`) | ESDB revision `1e5b7d3` (2026-06-24), app revision `156fb4e` | MIT-like (Kevin Atkinson; the copyright notice and permission notice must accompany copies, see ATTRIBUTION.md) | 172,417 -> 169,510 |

Entries are tokenised like the training text (`lmtool/validity.py`): hyphens, spaces and punctuation split them
("e-mail" -> `e`, `mail`; "dr." -> `dr`), parts with digits are dropped, letters are lower-cased and apostrophes
straightened. A vocabulary word is then validated when it is in the list, when its CASE flag is set (names,
places, brands, acronyms), or when it occurs at least once per million training tokens; it is *listed* when the list
carries it in lower case. Counts for the shipped models are in README.md ("Word validity") and in each model's META
`validity` key.

## Choosing the data mix

Measured on `tools/kbbench/corpus/dev.jsonl` with the small profile's samples, same pruning within a language (nl
near-unpruned, en thresholds 20/40 with caps 64/24; the shipped pruning is better for both, see README.md):

| Mix | nl KSR | nl top-3 | mix KSR | mix OOV | en KSR | en top-3 |
|---|---|---|---|---|---|---|
| subtitles only | 52.0% | 34.0% | 44.1% | 4.1% | 51.6% | 30.8% |
| subtitles + 30M web tokens (small profile) | 52.1% | 33.8% | 46.5% | 1.3% | 51.9% | 32.1% |

Subtitles match the conversational benchmark style best. The web sample adds written vocabulary ("afspraak",
"feature", "WhatsApp") and mainly helps the Dutch-English mixed sentences and OOV rate. A second 30M-token web
sample did not pay off at the final pruning: replacing a quarter of the English subtitles with it gave KSR 54.8%
vs 54.9%, and adding it on top gave 55.2% but an 8.3 MB file. It was not adopted for the small profile. With the
larger budget of the large profile, more subtitles and a 210M-token (nl) or 90M-token (en) web share paid off,
mainly for the mixed sentences (README.md, "Profiles").
