#!/usr/bin/env python3
"""Build the tiny n-gram fixtures for the Kotlin reader tests and their Python-reference expectations.

Runs the real pipeline (normalise -> count -> Kneser-Ney + pruning -> binary export) on a small synthetic corpus,
then records what the Python reader (lmtool.binfmt.NgramModel) returns for a fixed set of queries.

  .venv/bin/python make_fixture.py                 # lib/prediction/src/test/resources/ngram/tiny{3,4}.bin + .expected.tsv
                                                   # (format v2) and tiny3v1.bin (the same model as format v1)
  .venv/bin/python make_fixture.py --assets en nl  # <lang>.asset.expected.tsv for the production assets
"""

from __future__ import annotations

import os
import random
import shutil
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from lmtool import binfmt, count, kn, textnorm, validity  # noqa: E402

REPO = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(REPO, "lib", "prediction", "src", "test", "resources", "ngram")
WORK = os.path.join(HERE, "work", "fixture")

SUBJECTS = ["I", "you", "we", "they", "Sarah", "my brother", "ik", "je", "we"]
VERBS = ["don't know", "think", "want to go to", "will meet you in", "can't find", "am going to", "love",
         "ga naar", "woon in", "heb een", "wil graag"]
OBJECTS = ["Amsterdam", "the station", "the café", "my iPhone", "the FBI", "één keer", "de stad", "het huis",
           "a naïve idea", "the meeting", "dinner tonight", "the office", "Utrecht", "een fiets"]
TAILS = ["", "", "tomorrow", "today", "again", "morgen", "vandaag", "please", "now"]
OPENERS = ["", "", "", "Well,", "Oh,", "Hey,", "Nou,", "Ja,", "So"]


FILLERS = ("really just maybe also still never always quite very so much more less first then later soon here there "
           "echt gewoon misschien ook nog nooit altijd heel erg zo veel meer minder eerst dan later straks hier daar "
           "zeer naïef één twee drie don't won't it's we're they're Bob Lisa Mark Anna NASA eBay TV").split()


# Fixture validity: every lower-case token of the templates is "listed" except these, so they are valid only through
# frequency (FIXTURE_MIN_PPM is above every filler's share) or capitals; they stay invalid.
FIXTURE_UNLISTED = {"straks", "gewoon", "echt", "zeer"}
FIXTURE_MIN_PPM = 30000.0


def fixture_wordlist(path: str) -> None:
    words = set()
    for part in SUBJECTS + VERBS + OBJECTS + TAILS + OPENERS + FILLERS:
        for tok in textnorm.word_tokens(textnorm.nfkc(part)):
            w = textnorm.normalize_word(tok)
            if w not in FIXTURE_UNLISTED and tok == tok.lower():
                words.add(w)
    with open(path, "w", encoding="utf-8") as f:
        f.write("# make_fixture.py word list\n" + "\n".join(sorted(words)) + "\n")


def sentence(rng: random.Random) -> str:
    parts = [rng.choice(OPENERS), rng.choice(SUBJECTS), rng.choice(VERBS), rng.choice(OBJECTS), rng.choice(TAILS)]
    for _ in range(rng.choice([0, 0, 1, 2])):
        parts.insert(rng.randrange(1, len(parts) + 1), rng.choice(FILLERS))
    s = " ".join(p for p in parts if p)
    s = s[0].upper() + s[1:]
    return s + rng.choice([".", "!", "?", "..."])


def build_corpus(path: str, n: int = 4000) -> None:
    rng = random.Random(20261107)
    with open(path, "w", encoding="utf-8") as f:
        for _ in range(n):
            k = rng.choice([1, 1, 1, 2, 3])
            line = " ".join(sentence(rng) for _ in range(k))
            if rng.random() < 0.05:
                line = "- " + line + " - " + sentence(rng)
            segs = textnorm.segments_original_case(line, "en", subtitle=True)
            if segs:
                f.write("\t".join(" ".join(s) for s in segs) + "\n")


def fmt(x: float) -> str:
    x = np.float32(x)
    if np.isneginf(x):
        return "-inf"
    return repr(float(x))


def cands(lst) -> str:
    return ";".join(f"{i}:{fmt(lp)}" for i, lp in lst)


def expectations(m: binfmt.NgramModel, path: str) -> int:
    rng = random.Random(7)
    lines = [f"V\t{m.vocabulary_size}"]
    for i in range(m.vocabulary_size):
        lines.append(f"W\t{i}\t{m.word(i)}\t{m.cased_form(i)}\t{int(m.is_capitalized(i))}\t{fmt(m.unigram_logprob(i))}"
                     f"\t{int(m.is_valid(i))}\t{int(m.is_listed(i))}")
    V = m.vocabulary_size
    ids = list(range(V))
    contexts = [(-1, -1), (0, -1), (0, 0)]
    for p1 in ids:
        contexts.append((p1, -1))
        contexts.append((p1, 0))
    for _ in range(100):
        contexts.append((rng.randrange(-1, V), rng.randrange(-1, V)))
    stored = [(v, int(m.tc_u[j])) for v in range(V) for j in range(m.tcstart[v], m.tcstart[v + 1])]
    contexts += rng.sample(stored, min(150, len(stored)))
    contexts = list(dict.fromkeys(contexts))
    for p1, p2 in contexts:
        for w in rng.sample(ids, min(5, V)) + [-1, 0, V]:
            lines.append(f"L\t{w}\t{p1}\t{p2}\t{fmt(m.logprob(w, p1, p2) if 0 <= w < V else float('-inf'))}")
        for limit in (1, 3, 10):
            lines.append(f"N\t{p1}\t{p2}\t{limit}\t{cands(m.top_next(p1, p2, limit))}")
    prefixes = ["", "a", "d", "do", "don", "dont", "don'", "ee", "een", "één", "caf", "café", "na", "Am", "AMS", "i",
                "ip", "f", "fbi", "s", "st", "t", "th", "w", "zz", "xyz", "'", "h", "m", "my"]
    words = [m.word(i) for i in range(1, V)]
    prefixes += sorted({w[:k] for w in rng.sample(words, min(40, len(words))) for k in (1, 2, 3)})
    prefixes = list(dict.fromkeys(prefixes))
    for pre in prefixes:
        lo, hi = m.prefix_range(pre)
        lines.append(f"R\t{pre}\t{lo}\t{hi}")
        for p1, p2 in rng.sample(contexts, min(10, len(contexts))) + [(0, -1), (-1, -1)]:
            for limit in (3, 8):
                lines.append(f"C\t{pre}\t{p1}\t{p2}\t{limit}\t{cands(m.top_completions(pre, p1, p2, limit))}")
    for w in words + ["<s>", "unknownword", "Amsterdam", "don’t", ""]:
        lines.append(f"I\t{w}\t{m.word_id(w)}")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    return len(lines)


def asset_expectations(lang: str) -> None:
    """Small reference sample for a production asset (checked by NgramAssetTest when the asset exists)."""
    path = os.path.join(REPO, "app", "src", "main", "assets", "ime", "dict", "ngram", f"{lang}.bin")
    m = binfmt.NgramModel.load(path)
    rng = random.Random(11)
    V = m.vocabulary_size
    top = [int(i) for i in m.utop[:300]]
    lines = [f"V\t{V}"]
    for i in sorted(set(rng.sample(range(V), 400) + top[:50] + [0, 1, V - 1])):
        lines.append(f"W\t{i}\t{m.word(i)}\t{m.cased_form(i)}\t{int(m.is_capitalized(i))}\t{fmt(m.unigram_logprob(i))}"
                     f"\t{int(m.is_valid(i))}\t{int(m.is_listed(i))}")
    contexts = [(0, -1), (-1, -1)] + [(rng.choice(top), rng.choice(top + [0])) for _ in range(150)] + \
        [(rng.randrange(1, V), rng.randrange(0, V)) for _ in range(30)]
    stored = [(v, int(m.tc_u[j])) for v in rng.sample(top, 40) for j in range(m.tcstart[v], m.tcstart[v + 1])]
    contexts += rng.sample(stored, min(60, len(stored)))
    for p1, p2 in contexts:
        for w in rng.sample(top, 3) + [rng.randrange(1, V)]:
            lines.append(f"L\t{w}\t{p1}\t{p2}\t{fmt(m.logprob(w, p1, p2))}")
        lines.append(f"N\t{p1}\t{p2}\t5\t{cands(m.top_next(p1, p2, 5))}")
    words = [m.word(i) for i in rng.sample(range(1, V), 60)] + [m.word(i) for i in top[:40]]
    prefixes = sorted({w[:k] for w in words for k in (1, 2, 3, 4)}) + ["", "dont", "een", "ij"]
    for pre in prefixes:
        lo, hi = m.prefix_range(pre)
        lines.append(f"R\t{pre}\t{lo}\t{hi}")
        p1, p2 = rng.choice(contexts)
        lines.append(f"C\t{pre}\t{p1}\t{p2}\t3\t{cands(m.top_completions(pre, p1, p2, 3))}")
    for w in words[:50]:
        lines.append(f"I\t{w}\t{m.word_id(w)}")
    lines.append(f"VALID\t{int(m.valid.sum())}\t{int(m.listed.sum())}\t{m.version}")
    out = os.path.join(OUT, f"{lang}.asset.expected.tsv")
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"{out}: {len(lines)} expectations")


def main() -> None:
    if len(sys.argv) > 1 and sys.argv[1] == "--assets":
        for lang in sys.argv[2:] or ["en", "nl"]:
            asset_expectations(lang)
        return
    shutil.rmtree(WORK, ignore_errors=True)
    os.makedirs(WORK)
    os.makedirs(OUT, exist_ok=True)
    prep = os.path.join(WORK, "tiny.tsv")
    build_corpus(prep)
    cdir = os.path.join(WORK, "counts")
    count.run([prep], cdir, [], vocab_size=100000, min_count=1, min_df=1, workers=2, chunk_bytes=1 << 16)
    cfg = kn.PruneConfig(bigram_threshold=0.5, trigram_threshold=1.0, bigram_cap=6, bos_bigram_cap=10,
                         trigram_cap=4, trigram_min_count=2)
    model = kn.estimate(cdir, cfg, log=lambda s: None)
    wl = os.path.join(WORK, "wordlist.txt")
    fixture_wordlist(wl)
    valid, listed, vstats = validity.validity_from_counts(cdir, [wl], FIXTURE_MIN_PPM)
    for eb in (3, 4):
        path = os.path.join(OUT, f"tiny{eb}.bin")
        info = binfmt.write_model(path, cdir, model, {"lang": "en", "fixture": True, "validity": vstats},
                                  entry_bytes=eb, valid=valid, listed=listed)
        m = binfmt.NgramModel.load(path)
        n = expectations(m, os.path.join(OUT, f"tiny{eb}.expected.tsv"))
        print(f"{path}: {info['bytes']} bytes, vocab {info['vocab']}, valid {info['valid']}, bigrams {info['bigrams']}, "
              f"trigram contexts {info['trigram_contexts']}, trigrams {info['trigrams']}, {n} expectations")
    # The same model in format version 1 (no VALD section) for the backward-compatibility tests.
    path = os.path.join(OUT, "tiny3v1.bin")
    info = binfmt.write_model(path, cdir, model, {"lang": "en", "fixture": True}, entry_bytes=3)
    assert binfmt.NgramModel.load(path).version == 1
    print(f"{path}: {info['bytes']} bytes, format version {info['version']}")


if __name__ == "__main__":
    main()
