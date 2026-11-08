# Ownkey n-gram model format, versions 1 and 2

One file per language (`app/src/main/assets/ime/dict/ngram/<lang>.bin`). It holds a pruned trigram backoff
model built by `tools/lm` (interpolated modified Kneser-Ney, see `lmtool/kn.py`). Readers:

- Kotlin: `lib/prediction/src/main/kotlin/org/florisboard/lib/prediction/ngram/NgramModel.kt` (package
  `org.florisboard.lib.prediction.ngram`; `NgramWordModel` adapts it to the prediction engine). It reads the file in
  place from a `ByteBuffer` (in the app a read-only mapping of the uncompressed asset) with absolute little-endian
  gets, and only builds the offset indexes that the varint and length-prefixed sections need for random access.
- Python reference: `tools/lm/lmtool/binfmt.py` (`NgramModel`), also the writer

All integers are little-endian and unsigned unless noted. Log probabilities use the natural logarithm.

## Header (32 bytes)

| Offset | Type | Field |
|---|---|---|
| 0 | `u8[4]` | magic `OKNG` |
| 4 | `u16` | format version (`1` or `2`) |
| 6 | `u16` | section count `S` |
| 8 | `u32` | vocabulary size `V` (word ids `0..V-1`, id 0 is `<s>`) |
| 12 | `u8` | `idBits`: bits of the word id inside an n-gram entry |
| 13 | `u8` | `entryBytes`: bytes per n-gram entry (3 or 4) |
| 14 | `u16` | reserved (0) |
| 16 | `u32` | bigram entry count `NB` |
| 20 | `u32` | trigram context count `NC` |
| 24 | `u32` | trigram entry count `NT` |
| 28 | `u32` | total file size in bytes |

The section table follows at offset 32: `S` records of 12 bytes, each `u8[4] tag, u32 offset, u32 length`
(offsets from the start of the file, sections 4-byte aligned). Version 1 writes the sections in this order:
`META VOCB FOLD CASE UNIG UTOP QTBL BIGR TRIG`; version 2 adds `VALD` after `CASE`
(`META VOCB FOLD CASE VALD UNIG UTOP QTBL BIGR TRIG`). Readers look sections up by tag.

`qBits = 8 * entryBytes - idBits` (at most 12). An entry is an `entryBytes`-byte little-endian integer
`e = id | (code << idBits)`; `id = e & ((1 << idBits) - 1)` and `code = e >> idBits` indexes a codebook in `QTBL`.

## Sections

**META**: UTF-8 JSON. Keys used by readers: `lang` (for example `"nl"`). The rest records sources, pruning settings
and counts for provenance.

**VOCB**: `u8 len[V]` (UTF-8 byte length of each word), then the concatenated UTF-8 bytes. Word 0 is `<s>`.
Words are lower case and contain only letters and internal straight apostrophes. Ids `1..V-1` are sorted by
`(fold(word), word)` compared as UTF-8 bytes (unsigned), so all words whose folded form starts with a folded
prefix occupy one contiguous id range.

`fold(word)` = Unicode NFD, then remove combining marks (category Mn) and `'`. Example: `één -> een`,
`don't -> dont`.

**FOLD**: `u32 n; u32 ids[n]` (ascending) `; u8 len[n]; bytes`: the folded form of every word whose folded form
differs from the word itself. All other words fold to themselves.

**CASE**: `u8 flags[V]; u32 n; u32 ids[n]; u8 len[n]; bytes`.
Flags: `0` lower case, `1` title case (first letter upper, for example `Amsterdam`, `I'm`), `2` all upper
(`FBI`), `4` other: the cased form is stored in the table that follows (`iPhone`, `McDonald's`). A flag is set
when at least 60% of at least 3 mid-sentence occurrences (not sentence-initial, not in all-caps lines) are
capitalised.

**VALD** (version 2 only, required there): `u8 valid[ceil(V / 8)]; u8 listed[ceil(V / 8)]`, two bit sets in which bit
`id & 7` of byte `id >> 3` stands for word `id`; bit 0 (`<s>`) is never set. A `valid` bit marks a *validated* word,
that is really a word and not only a token of the training text (a subtitle typo, OCR debris, a truncation). A
`listed` bit marks a word of the curated word list of the language (a dictionary word; every listed word is also
valid). `lmtool/validity.py` sets `listed` when the word (lower case, straight apostrophe) is in the list (OpenTaal
for nl, SCOWL/ESDB for en, see SOURCES.md), and `valid` when it is listed, when its CASE flag is set (names, places,
brands, acronyms), or when it occurs at least `--valid-min-ppm` times per million training tokens (META key
`validity` records the threshold and counts). Readers of version 1 files treat every word as valid and none as listed (without a word list, names and slang
cannot be told from dictionary words).

**UNIG**: `u16 q[V]`; `log P(w) = -q / 1024`. `q = 0xFFFF` marks `<s>` (never predicted).

**UTOP**: `u32 n; u32 ids[n]`: the `n` (1024) most probable word ids, sorted by unigram probability descending
(ties by id). Used by the next-word fallback.

**QTBL**: `f32 bigram[2^qBits]; f32 trigram[2^qBits]; f32 bigramBackoff[256]; f32 trigramBackoff[256]`.
Codebooks of log probabilities and log backoff weights (1-D k-means). `bigramBackoff[0]` is exactly `0.0`.

**BIGR**: `varint len[V]` (number of successors of each context word, LEB128), `u8 bowCode[V]` (log backoff weight
of each context word, index into `bigramBackoff`), then `NB` entries. The successors of context word `v` are
entries `start(v) .. start(v) + len[v]` with `start(v) = sum(len[0..v-1])`, sorted by probability descending.
Each entry value is `log P(w | v)`.

**TRIG**: `varint count[V]` (number of trigram contexts whose previous word `prev1` is `v`), then `NC` context
records of 5 bytes sorted by `(prev1, prev2)`: `u24 prev2, u8 len, u8 bowCode` (index into `trigramBackoff`), then
`NT` entries. Context `c` owns entries `start(c) .. start(c) + len` (prefix sum of `len`), sorted by probability
descending. Each entry value is `log P(w | prev2, prev1)`. `prev2` may be `<s>` (second word of a sentence).

## Probability lookup

```
logProb(w | p2, p1):
  acc = 0
  if p1 is a word (not <s>, not unknown) and p2 is known and context (p2, p1) exists:
      if w in its trigram list: return entry
      acc += trigramBackoff(p2, p1)
  if p1 is known (a word or <s>):
      if w in the bigram list of p1: return acc + entry
      acc += bigramBackoff(p1)
  return acc + unigram(w)
```

Backoff weights were renormalised after pruning, so this is a normalised distribution over the vocabulary
(minus the mass of unknown words). At the start of a sentence use `p1 = <s>` (then `p2` is ignored).
`topNext` merges the trigram list, the bigram list (+ trigram backoff) and `UTOP` (+ both backoffs); because
each list is sorted, the merged top-k is exact. `topCompletions` does the same restricted to the folded-prefix id
range, scanning the range for the best unigram-backoff words.

## Versioning

Readers must reject unknown versions. Additive changes (new sections) keep the version when old readers can ignore
them; any change to an existing section's layout bumps the version. Version 2 bumped the version for `VALD` even
though old readers could skip it, because ignoring it would make every junk word count as a trusted word again; the
Kotlin and Python readers accept both versions. `lmtool build` writes version 2 when it gets `--wordlist`, else
version 1.
