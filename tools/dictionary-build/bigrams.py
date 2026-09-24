#!/usr/bin/env python3
"""Builds the per-language bigram lists used as word context by the Latin autocorrect engine.

Source: Tatoeba per-language sentence exports (CC BY 2.0 FR), downloaded into --cache on first run.

Sentences whose id is divisible by HOLDOUT_MODULUS are held out: they never enter the bigram counts, so the
contextual benchmark in app/src/test/resources/autocorrect/ can sample from them. The clean-text benchmark
sentences are excluded as well.

Output: app/src/main/assets/ime/dict/latin/{en,nl}.bigrams.txt

    # ownkey-latin-bigrams v1 format=log100
    <s>\tik 812 het 640 ...
    ik\tben 790 heb 702 ...

One line per previous word, "<s>" for the start of a sentence. Each successor is followed by round(100 * ln(count)).
Only words in the built dictionary ({en,nl}.txt) are counted, and only pairs seen at least MIN_COUNT times.

Usage: py -3 tools/dictionary-build/bigrams.py [--cache DIR] [--min-count N] [--probe]
"""

from __future__ import annotations

import argparse
import bz2
import gzip
import math
import re
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "ime" / "dict" / "latin"
BENCHMARK = ROOT / "app" / "src" / "test" / "resources" / "autocorrect"
TATOEBA_URL = "https://downloads.tatoeba.org/exports/per_language/{lang}/{lang}_sentences.tsv.bz2"
LANGUAGES = {"en": "eng", "nl": "nld"}
HOLDOUT_MODULUS = 10
MIN_COUNT = 2
SENTENCE_START = "<s>"
# Tatoeba's English and Dutch sentences use a few stock names very often ("Tom", "Mary"). As a context they are
# fine, but as a predicted next word they would be odd, so their counts are capped at this share of the
# successor total of each previous word.
STOCK_NAMES = {"tom", "mary", "john", "alice", "ken", "bob", "jack", "jane", "maria", "sami", "layla"}
STOCK_NAME_MAX_SHARE = 0.02
TOKEN = re.compile(r"[^\W\d_]+(?:['’-][^\W\d_]+)*")
SENTENCE_BREAK = re.compile(r"[.!?;:]+")


def fetch(url: str, target: Path) -> Path:
    if not target.exists():
        print(f"downloading {url}")
        urllib.request.urlretrieve(url, target)
    return target


def load_vocabulary(language: str) -> set[str]:
    words = set()
    for line in (ASSETS / f"{language}.txt").read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        words.add(line.split("\t", 1)[0])
    return words


def benchmark_sentences(language: str) -> set[str]:
    path = BENCHMARK / f"clean_{language}.txt"
    if not path.exists():
        return set()
    return {line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()}


def tokenize(text: str) -> list[list[str]]:
    """Splits text into sentence parts of lowercase word tokens. Commas do not break a part; . ! ? ; : do."""
    parts = []
    for chunk in SENTENCE_BREAK.split(text):
        tokens = [token.replace("’", "'").lower() for token in TOKEN.findall(chunk)]
        if tokens:
            parts.append(tokens)
    return parts


def count_bigrams(path: Path, vocabulary: set[str], excluded: set[str]) -> tuple[Counter, int, int]:
    counts: Counter = Counter()
    sentences = 0
    tokens = 0
    with bz2.open(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) != 3:
                continue
            sentence_id, text = int(parts[0]), parts[2].strip()
            if sentence_id % HOLDOUT_MODULUS == 0 or text in excluded:
                continue
            sentences += 1
            for words in tokenize(text):
                tokens += len(words)
                previous = SENTENCE_START
                for word in words:
                    if word not in vocabulary:
                        previous = None
                        continue
                    if previous is not None:
                        counts[(previous, word)] += 1
                    previous = word
    return counts, sentences, tokens


def cap_stock_names(counts: Counter) -> Counter:
    totals: Counter = Counter()
    for (previous, _), count in counts.items():
        totals[previous] += count
    capped = Counter()
    for (previous, word), count in counts.items():
        if word in STOCK_NAMES:
            count = min(count, max(1, int(totals[previous] * STOCK_NAME_MAX_SHARE)))
        capped[(previous, word)] = count
    return capped


def render(counts: Counter, min_count: int, source_note: str) -> str:
    successors: dict[str, list[tuple[str, int]]] = defaultdict(list)
    for (previous, word), count in counts.items():
        if count >= min_count:
            successors[previous].append((word, count))
    lines = [
        "# ownkey-latin-bigrams v1 format=log100",
        f"# {source_note}",
    ]
    for previous in sorted(successors, key=lambda p: (-sum(c for _, c in successors[p]), p)):
        items = sorted(successors[previous], key=lambda item: (-item[1], item[0]))
        rendered = " ".join(f"{word} {round(100 * math.log(count))}" for word, count in items)
        lines.append(f"{previous}\t{rendered}")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, default=Path.home() / ".cache" / "ownkey-dictionary-build")
    parser.add_argument("--min-count", type=int, default=MIN_COUNT)
    parser.add_argument("--probe", action="store_true", help="print sizes for several minimum counts, write nothing")
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)

    for language, tatoeba in LANGUAGES.items():
        path = fetch(TATOEBA_URL.format(lang=tatoeba), args.cache / f"{tatoeba}_sentences.tsv.bz2")
        vocabulary = load_vocabulary(language)
        counts, sentences, tokens = count_bigrams(path, vocabulary, benchmark_sentences(language))
        counts = cap_stock_names(counts)
        note = (f"language={language} source=Tatoeba (CC BY 2.0 FR) training_sentences={sentences} "
                f"tokens={tokens} min_count={args.min_count}")
        if args.probe:
            print(f"{language}: {sentences} sentences, {tokens} tokens, {len(counts)} distinct bigrams")
            for min_count in (1, 2, 3, 5):
                text = render(counts, min_count, note)
                kept = sum(1 for count in counts.values() if count >= min_count)
                mass = sum(count for count in counts.values() if count >= min_count) / max(1, sum(counts.values()))
                print(f"  min {min_count}: {kept} bigrams, {mass:.1%} of pair occurrences, "
                      f"{len(text.encode()) / 1e6:.2f} MB text, {len(gzip.compress(text.encode())) / 1e6:.2f} MB gzip")
            continue
        target = ASSETS / f"{language}.bigrams.txt"
        target.write_text(render(counts, args.min_count, note), encoding="utf-8", newline="\n")
        print(f"{target.name}: {sum(1 for c in counts.values() if c >= args.min_count)} bigrams")


if __name__ == "__main__":
    main()
