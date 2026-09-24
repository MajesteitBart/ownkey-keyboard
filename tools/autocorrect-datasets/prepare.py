#!/usr/bin/env python3
"""Builds the downloaded parts of the autocorrect benchmark datasets.

Writes into app/src/test/resources/autocorrect/:
  clean_en.txt, clean_nl.txt       correctly written sentences (Tatoeba, CC BY 2.0 FR)
  context_en.txt, context_nl.txt   held-out sentences for typos in context (Tatoeba, CC BY 2.0 FR); their ids
                                   are divisible by HOLDOUT_MODULUS, so tools/dictionary-build/bigrams.py never
                                   counts them
  real_en_wikipedia.tsv            typo<TAB>intended pairs (Wikipedia, CC BY-SA 4.0)

Hand-written lists in the same folder (real_*_curated.tsv, apostrophes_en.tsv, oov.txt)
are maintained by hand and not touched by this script.

Usage: py -3 tools/autocorrect-datasets/prepare.py [--cache DIR] [--only NAME]
"""
from __future__ import annotations

import argparse
import bz2
import random
import re
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app" / "src" / "test" / "resources" / "autocorrect"

TATOEBA_URL = "https://downloads.tatoeba.org/exports/per_language/{lang}/{lang}_sentences.tsv.bz2"
WIKIPEDIA_URL = (
    "https://en.wikipedia.org/w/index.php?"
    "title=Wikipedia:Lists_of_common_misspellings/For_machines&action=raw"
)

SEED = 20260924
TARGET_WORDS = 8000
# Must match tools/dictionary-build/bigrams.py.
HOLDOUT_MODULUS = 10
CONTEXT_SENTENCES = 2000

ALLOWED = {
    "eng": re.compile(r"^[A-Za-z ,.!?'’:;\"-]+$"),
    "nld": re.compile(r"^[A-Za-zà-ÿÀ-Ý ,.!?'’:;\"-]+$"),
}


def fetch(url: str, target: Path) -> Path:
    if not target.exists():
        print(f"downloading {url}")
        urllib.request.urlretrieve(url, target)
    return target


def build_clean_text(cache: Path, lang: str, out_name: str) -> None:
    path = fetch(TATOEBA_URL.format(lang=lang), cache / f"{lang}_sentences.tsv.bz2")
    sentences = []
    with bz2.open(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) != 3:
                continue
            text = parts[2].strip()
            words = text.split()
            if not 4 <= len(words) <= 20:
                continue
            if not ALLOWED[lang].match(text):
                continue
            sentences.append((int(parts[0]), text))
    sentences.sort()
    rng = random.Random(SEED)
    rng.shuffle(sentences)
    picked, count = [], 0
    for _, text in sentences:
        picked.append(text)
        count += len(text.split())
        if count >= TARGET_WORDS:
            break
    (OUT / out_name).write_text("\n".join(picked) + "\n", encoding="utf-8", newline="\n")
    print(f"{out_name}: {len(picked)} sentences, {count} words")


def build_context_sentences(cache: Path, lang: str, out_name: str, clean_name: str) -> None:
    path = fetch(TATOEBA_URL.format(lang=lang), cache / f"{lang}_sentences.tsv.bz2")
    clean = set((OUT / clean_name).read_text(encoding="utf-8").splitlines())
    sentences = []
    with bz2.open(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) != 3:
                continue
            sentence_id, text = int(parts[0]), parts[2].strip()
            if sentence_id % HOLDOUT_MODULUS != 0 or text in clean:
                continue
            if not 4 <= len(text.split()) <= 20 or not ALLOWED[lang].match(text):
                continue
            sentences.append((sentence_id, text))
    sentences.sort()
    rng = random.Random(SEED + 1)
    rng.shuffle(sentences)
    picked = [text for _, text in sentences[:CONTEXT_SENTENCES]]
    (OUT / out_name).write_text("\n".join(picked) + "\n", encoding="utf-8", newline="\n")
    print(f"{out_name}: {len(picked)} sentences")


def build_wikipedia_misspellings(cache: Path) -> None:
    path = fetch(WIKIPEDIA_URL, cache / "wiki_misspellings.txt")
    pairs = []
    seen = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if "->" not in line:
            continue
        typo, intended = (part.strip() for part in line.split("->", 1))
        if "," in intended or " " in intended:
            continue
        if not re.fullmatch(r"[a-z']+", typo) or not re.fullmatch(r"[a-z']+", intended):
            continue
        if typo == intended or typo in seen:
            continue
        seen.add(typo)
        pairs.append((typo, intended))
    lines = [f"{typo}\t{intended}" for typo, intended in pairs]
    (OUT / "real_en_wikipedia.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"real_en_wikipedia.tsv: {len(pairs)} pairs")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, default=Path.home() / ".cache" / "ownkey-autocorrect-datasets")
    parser.add_argument("--only", choices=["clean", "context", "wikipedia"])
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    if args.only in (None, "clean"):
        build_clean_text(args.cache, "eng", "clean_en.txt")
        build_clean_text(args.cache, "nld", "clean_nl.txt")
    if args.only in (None, "context"):
        build_context_sentences(args.cache, "eng", "context_en.txt", "clean_en.txt")
        build_context_sentences(args.cache, "nld", "context_nl.txt", "clean_nl.txt")
    if args.only in (None, "wikipedia"):
        build_wikipedia_misspellings(args.cache)


if __name__ == "__main__":
    main()
