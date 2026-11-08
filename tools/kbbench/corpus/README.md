# kbbench sentence corpus

These are the sentences the benchmark typist types when comparing Ownkey with Gboard. The protocol is in
[`../PROTOCOL.md`](../PROTOCOL.md).

> **Do not train on this corpus.** Do not use any sentence here, or its source collection, to train, build,
> fine-tune or tune any language model, n-gram table, dictionary, autocorrect model or suggestion ranking.
> That covers the authored files and Tatoeba (the benchmark treats Tatoeba as test-only). The `dev` split is for
> checking behaviour while tuning: run it through the simulator or on the device. Do not use it as data. The `test`
> split is for headline numbers only.

## Files

| File | Contents |
|---|---|
| `dev.jsonl`, `test.jsonl` | The corpus: one JSON object per line (schema below). Generated, do not edit by hand. |
| `authored_en.txt`, `authored_nl.txt`, `authored_mix.txt` | Sentences written for this benchmark, one per line: `split<TAB>text`. The mix file adds a third column `term\|term` naming the embedded English words, and the builder checks that each one appears in the sentence. |
| `build_corpus.py` | Builder and validator (Python 3, stdlib only, deterministic). |
| `raw/` | Tatoeba exports downloaded by the builder. Git ignores this folder. |

Each split has 340 sentences:

| lang | authored | Tatoeba | total |
|---|---|---|---|
| `en` | 100 | 50 | 150 |
| `nl` | 100 | 50 | 150 |
| `mix` | 40 | 0 | 40 |

IDs are `<lang>-<split>-<nnn>`. Authored sentences come first (001-100, or 001-040 for mix), then Tatoeba (101-150).

## Record schema

```json
{"id": "nl-dev-037", "split": "dev", "lang": "nl", "source": "authored",
 "text": "Ik heb de foto's van de vakantie in de gedeelde map gezet.",
 "words": ["ik", "heb", "de", "foto's", "van", "de", "vakantie", "in", "de", "gedeelde", "map", "gezet"]}
```

- `lang`: `en`, `nl`, or `mix`. A mix sentence is Dutch with 1 to 3 English words or phrases in it, the way Dutch
  professionals write ("Ik werk vandaag remote, dus de standup doe ik via Teams.").
- `source`: `authored`, or `tatoeba:<sentence id>`.
- `text`: the natural sentence, with casing and punctuation.
- `words`: the words the typist intends, derived from `text`. They are lowercase and NFC, with punctuation removed.
  Apostrophes inside a word stay (`don't`, `foto's`, `zo'n`), and a typographic `’` becomes `'`. Diacritics stay
  (`één`, `ideeën`, `café`). The typist types the folded form (`dont`, `fotos`, `een`), as PROTOCOL.md describes.

## Constraints (enforced by the validator)

- The text contains only letters that fold to a-z, spaces, apostrophes and sentence punctuation (`. , ! ? ; : " ( ) …`).
  It contains no digits, hyphens or dashes, URLs, emoji or other symbols. Tokens with punctuation inside them
  (`U.S.`, `wait...what`) are rejected, and so are apostrophes at the start or end of a word (`'s avonds`, `boys'`).
- Authored sentences have 4 to 16 words. Tatoeba sentences have 5 to 16.
- `words` must equal the normalisation of `text`.
- Exact sizes per split, language and source, as in the table above.
- No duplicates or near-duplicates, within a split or across dev and test. The check compares folded words. Two
  sentences are near-duplicates when they have the same words, when their word sets overlap by a Jaccard index of
  0.8 or more, or when one word edit turns one into the other (two edits for sentences of 10 words or more).
- The authored entries in the JSONL match the authored files. Each Tatoeba text matches the raw export, checked
  when `raw/` is present.

## Sources and licences

**Authored** (`source: authored`): written for this benchmark in November 2026. The English and Dutch sentences
read like what a Dutch tech founder or consultant, and their friends and family, type on a phone. They cover chat,
short emails, to-do notes, scheduling, family logistics, work, sport, food, travel, health, shopping and tech. The
Dutch is Netherlands Dutch. These sentences are released under the repository's licence (Apache-2.0).

**Tatoeba** (`source: tatoeba:<id>`): sentences from the [Tatoeba](https://tatoeba.org) collection, licensed under
[CC BY 2.0 FR](https://creativecommons.org/licenses/by/2.0/fr/). Attribution: *Sentences from Tatoeba
(https://tatoeba.org), contributed by the Tatoeba community, CC BY 2.0 FR.* Each sentence keeps its Tatoeba id, so
you can find its contributor and history at `https://tatoeba.org/en/sentences/show/<id>`. The sentence text is
unchanged apart from Unicode NFC and whitespace normalisation.

The exports used for the current files were downloaded on 2026-11-07:

| Export | sha256 |
|---|---|
| `per_language/eng/eng_sentences.tsv.bz2` | `1a2d75ac07aa5d035bff4e2bf053139dfd3b1f1645f6fd3d76fc852cc9ef6020` |
| `per_language/nld/nld_sentences.tsv.bz2` | `89257f5caaa833bca7f9ab683412f6f924254c6f4ca26ff217327be23c2b1b06` |

### How Tatoeba sentences are chosen

1. Sentences must meet the constraints above, have 5 to 16 words, and end in `.`, `!`, `?` or `…`.
2. Conversational filter: the sentence must contain a first or second person word (`I`, `you`, `we`, `my`, `let's`;
   `ik`, `je`, `jij`, `we`, `mijn`, `u`, ...).
3. Some sentences are excluded: abbreviations (`Mr.`, `bijv.`), all-caps words other than `OK`, stray single letters,
   and the large Tatoeba topic clusters (language advocacy, recurring fictional casts). A short id blocklist in
   `build_corpus.py` holds sentences rejected on review.
4. Sentences are ordered by `sha1(id)` and accepted in that order, skipping any near-duplicate of a sentence
   already in the corpus. The low bit of `sha1("split:" + id)` decides each sentence's split, so dev and test are
   disjoint, and removing one sentence never moves another to the other split. At most 8 sentences per split and
   language may mention Tom or Mary.

## Rebuilding and checking

From the repository root:

```sh
python3 -I tools/kbbench/corpus/build_corpus.py           # download raw/ if missing, rebuild both splits, validate
python3 -I tools/kbbench/corpus/build_corpus.py --check   # validate the committed dev/test.jsonl only
```

Tatoeba refreshes its exports regularly, and a newer export can change which sentences the sampler picks. The
committed `dev.jsonl` and `test.jsonl` are the canonical corpus. Once baselines exist, any change to the corpus
(new export, edited authored line, changed rule) means re-running every keyboard, as PROTOCOL.md requires.
