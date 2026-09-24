#!/usr/bin/env python3
"""Builds the downloaded parts of the autocorrect benchmark datasets.

Writes into app/src/test/resources/autocorrect/:
  clean_en.txt, clean_nl.txt       correctly written sentences (Tatoeba, CC BY 2.0 FR)
  real_en_wikipedia.tsv            typo<TAB>intended pairs (Wikipedia, CC BY-SA 4.0)

Hand-written lists in the same folder (real_*_curated.tsv, apostrophes_en.tsv, oov.txt)
are maintained by hand and not touched by this script.

Usage: py -3 tools/autocorrect-datasets/prepare.py [--cache DIR]
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
    (OUT / out_name).write_text("\n".join(picked) + "\n", encoding="utf-8")
    print(f"{out_name}: {len(picked)} sentences, {count} words")


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
    (OUT / "real_en_wikipedia.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"real_en_wikipedia.tsv: {len(pairs)} pairs")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, default=Path.home() / ".cache" / "ownkey-autocorrect-datasets")
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    build_clean_text(args.cache, "eng", "clean_en.txt")
    build_clean_text(args.cache, "nld", "clean_nl.txt")
    build_wikipedia_misspellings(args.cache)


if __name__ == "__main__":
    main()
