# Autocorrect benchmark datasets

These files are test data only. None of them ship in the app.

| File | Source | License |
| --- | --- | --- |
| `clean_en.txt`, `clean_nl.txt`, `context_en.txt`, `context_nl.txt` | Sentences from [Tatoeba](https://tatoeba.org), per-language exports | CC BY 2.0 FR |
| `real_en_wikipedia.tsv` | [Wikipedia: Lists of common misspellings/For machines](https://en.wikipedia.org/wiki/Wikipedia:Lists_of_common_misspellings/For_machines), single-word entries only | CC BY-SA 4.0 |
| `real_en_curated.tsv`, `apostrophes_en.tsv`, `real_nl_curated.tsv`, `oov_harness.txt` | Hand-written for the 2026-09-24 baseline harness | Apache-2.0, same as this repository |
| `real_nl_extra.tsv`, `oov.txt`, `realword_en.tsv`, `realword_nl.tsv` | Hand-written | Apache-2.0, same as this repository |

`tools/autocorrect-datasets/prepare.py` regenerates the downloaded files. A few Tatoeba sentences with a typo were corrected
by hand and one non-sentence was dropped; `CORRECTIONS` and `REJECTED` in that script list every change.
