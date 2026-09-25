#!/usr/bin/env python3
"""Builds the per-language bigram lists used as word context by the Latin autocorrect engine.

Source: Tatoeba per-language sentence exports (CC BY 2.0 FR), downloaded into --cache on first run.

Sentences whose id is divisible by HOLDOUT_MODULUS are held out: they never enter the bigram counts, so the
contextual benchmark in app/src/test/resources/autocorrect/ can sample from them. The clean-text benchmark
sentences are excluded as well.

Output: app/src/main/assets/ime/dict/latin/{en,nl}.bigrams.txt

    # ownkey-latin-bigrams v2 format=log100
    # language=nl source=...
    @words 18234
    <s>
    aan
    ...
    @pairs
    0\t12 1040 3 912 ...
    1\t...
    @notpredicted
    alice
    ...

The words section lists every word that occurs in a pair, sorted by code point; a word's id is its position, and
"<s>" stands for the start of a sentence. Each pairs line holds a previous-word id, then its successors in
ascending id order as (id difference to the previous successor, round(100 * ln(count))) pairs. Lines come in
ascending previous-word id order. The optional notpredicted section lists words the app never offers as a
next-word prediction. The app reads this without building strings or maps per pair.

Only words in the built dictionary ({en,nl}.txt) are counted, and only pairs seen at least MIN_COUNT times.

Usage: py -3 tools/dictionary-build/bigrams.py [--cache DIR] [--min-count N] [--probe]

--min-count overrides MIN_COUNT for both languages.
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
# English has twelve times the Dutch text, so it can drop rarer pairs and still cover more.
MIN_COUNT = {"en": 3, "nl": 2}
SENTENCE_START = "<s>"
# Words written with a capital in the middle of a sentence at least this often are names or other proper nouns
# ("Tom", "Mary", "English", "Nederland"). Tatoeba uses a few stock names constantly, and predictions are shown in
# lowercase, so proper nouns are listed as not to be predicted. They stay in the pair counts: removing them there
# would hand their share to common words and make lowercase names more likely to be "corrected". "I" and its
# contractions are predicted.
PROPER_NOUN_CAPITAL_SHARE = 0.5
PROPER_NOUN_MIN_OCCURRENCES = 3
ALWAYS_COUNTED = {"i", "i'm", "i've", "i'll", "i'd"}
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


def tokenize(text: str, lowercase: bool = True) -> list[list[str]]:
    """Splits text into sentence parts of word tokens. Commas do not break a part; . ! ? ; : do."""
    parts = []
    for chunk in SENTENCE_BREAK.split(text):
        tokens = [token.replace("’", "'") for token in TOKEN.findall(chunk)]
        if lowercase:
            tokens = [token.lower() for token in tokens]
        if tokens:
            parts.append(tokens)
    return parts


def proper_nouns(path: Path, excluded: set[str]) -> set[str]:
    """Lowercase forms of words that are usually capitalized when they are not the first word of a sentence."""
    capitalized: Counter = Counter()
    total: Counter = Counter()
    with bz2.open(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) != 3:
                continue
            sentence_id, text = int(parts[0]), parts[2].strip()
            if sentence_id % HOLDOUT_MODULUS == 0 or text in excluded:
                continue
            for words in tokenize(text, lowercase=False):
                for word in words[1:]:
                    lower = word.lower()
                    total[lower] += 1
                    if word[0].isupper():
                        capitalized[lower] += 1
    return {
        word for word, count in total.items()
        if count >= PROPER_NOUN_MIN_OCCURRENCES
        and capitalized[word] >= PROPER_NOUN_CAPITAL_SHARE * count
        and word not in ALWAYS_COUNTED
    }


def count_bigrams(path: Path, vocabulary: set[str], excluded: set[str], names: set[str]) -> tuple[Counter, int, int]:
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


def render(counts: Counter, min_count: int, source_note: str, not_predicted: set[str] = frozenset()) -> str:
    successors: dict[str, list[tuple[str, int]]] = defaultdict(list)
    for (previous, word), count in counts.items():
        if count >= min_count:
            successors[previous].append((word, count))
    words = sorted({w for p, items in successors.items() for w in [p] + [x for x, _ in items]})
    ids = {word: index for index, word in enumerate(words)}
    lines = [
        "# ownkey-latin-bigrams v2 format=log100",
        f"# {source_note}",
        f"@words {len(words)}",
        *words,
        "@pairs",
    ]
    for previous in sorted(successors, key=lambda p: ids[p]):
        items = sorted((ids[word], round(100 * math.log(count))) for word, count in successors[previous])
        last = 0
        fields = []
        for word_id, log_count in items:
            fields.append(f"{word_id - last} {log_count}")
            last = word_id
        lines.append(f"{ids[previous]}\t{' '.join(fields)}")
    listed = sorted(word for word in not_predicted if word in ids)
    if listed:
        lines.append("@notpredicted")
        lines.extend(listed)
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, default=Path.home() / ".cache" / "ownkey-dictionary-build")
    parser.add_argument("--min-count", type=int, default=None)
    parser.add_argument("--probe", action="store_true", help="print sizes for several minimum counts, write nothing")
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)

    for language, tatoeba in LANGUAGES.items():
        path = fetch(TATOEBA_URL.format(lang=tatoeba), args.cache / f"{tatoeba}_sentences.tsv.bz2")
        vocabulary = load_vocabulary(language)
        excluded = benchmark_sentences(language)
        names = proper_nouns(path, excluded)
        counts, sentences, tokens = count_bigrams(path, vocabulary, excluded, names)
        # 0 is a valid choice (keep every pair), so only a missing value falls back to the default.
        min_count = MIN_COUNT[language] if args.min_count is None else args.min_count
        note = (f"language={language} source=Tatoeba (CC BY 2.0 FR) training_sentences={sentences} "
                f"tokens={tokens} min_count={min_count} proper_nouns_not_predicted={len(names)}")
        if args.probe:
            print(f"{language}: {sentences} sentences, {tokens} tokens, {len(counts)} distinct bigrams")
            for min_count in (1, 2, 3, 5):
                text = render(counts, min_count, note, names)
                kept = sum(1 for count in counts.values() if count >= min_count)
                mass = sum(count for count in counts.values() if count >= min_count) / max(1, sum(counts.values()))
                print(f"  min {min_count}: {kept} bigrams, {mass:.1%} of pair occurrences, "
                      f"{len(text.encode()) / 1e6:.2f} MB text, {len(gzip.compress(text.encode())) / 1e6:.2f} MB gzip")
            continue
        target = ASSETS / f"{language}.bigrams.txt"
        target.write_text(render(counts, min_count, note, names), encoding="utf-8", newline="\n")
        print(f"{target.name}: {sum(1 for c in counts.values() if c >= min_count)} bigrams")


if __name__ == "__main__":
    main()
