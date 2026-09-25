#!/usr/bin/env python3
"""Builds the shipped Latin dictionaries (EN, NL) from their sources.

Sources (downloaded into --cache on first run):
  - FrequencyWords 2018 counts, as already shipped in app/src/main/assets/ime/dict/frequencywords/ (CC BY-SA 4.0)
  - SCOWL 2020.12.07, sizes up to 70 and names up to 95, for English validity (permissive notice license, see SCOWL Copyright)
  - OpenTaal wordlist for Dutch validity (BSD-3-Clause or CC BY 3.0)
  - Reviewed removal lists in app/src/main/assets/ime/dict/removals/

Output: app/src/main/assets/ime/dict/latin/{en,nl}.txt with one "word<TAB>value" line per word, where value is
round(100 * ln(count)). The Kotlin loader turns it back into a count.

Usage: py -3 tools/dictionary-build/build.py [--cache DIR]
"""
from __future__ import annotations

import argparse
import hashlib
import math
import re
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "ime" / "dict"
OUT_DIR = ASSETS / "latin"

# Both sources are pinned and checked, so a rebuild gives the same lists. OpenTaal is pinned to the last commit
# that changed wordlist.txt (2023-03-10), which is what the shipped nl.txt was built from.
SCOWL_URL = "https://downloads.sourceforge.net/project/wordlist/SCOWL/2020.12.07/scowl-2020.12.07.tar.gz"
SCOWL_SHA256 = "5587667caa20c4891390c2d42dbb4d5c4c3f41bee77af1457ece3ba23fb859cc"
OPENTAAL_URL = ("https://raw.githubusercontent.com/OpenTaal/opentaal-wordlist/"
                "08879a9cb02c54a0cb057acb621604131cb2f84f/wordlist.txt")
OPENTAAL_SHA256 = "12e5fb5e3c73840b583b30016926d1f63a75e9bf1652a3a6634b2ba7c49ad7be"

HEADER = "# ownkey-latin-dictionary v1 format=log100"

# Words ranked this high in the subtitle counts are kept even when the validity list lacks them
# (okay, gonna, yeah, common names).
KEEP_TOP_RANK = 3000
# Below that, a word missing from the validity list is still kept when it ranks in the top 20,000 and does not look
# like a misspelling: no word one edit away is at least MISSPELLING_RATIO times more frequent. This keeps names
# (sami, oleg) and drops typos (wich next to which).
KEEP_UNLISTED_RANK = 20000
MISSPELLING_RATIO = 10

# --- English contractions -------------------------------------------------------------------------------------

# The subtitle tokenizer split "don't" into "don" + "'t". These fragments carry the contraction's count.
EN_NT_FRAGMENTS = {
    "don": "don't", "didn": "didn't", "doesn": "doesn't", "isn": "isn't", "wasn": "wasn't", "weren": "weren't",
    "hasn": "hasn't", "haven": "haven't", "hadn": "hadn't", "couldn": "couldn't", "wouldn": "wouldn't",
    "shouldn": "shouldn't", "aren": "aren't", "ain": "ain't", "mustn": "mustn't", "needn": "needn't",
}
# Fragments that are also real words keep a small count of their own.
EN_REAL_FRAGMENT_COUNT = {"don": 3000, "haven": 3000}
# "can't" and "won't" split into "can"/"won" + "'t"; the rest of the "'t" count is theirs, split 3:1.
EN_CANT_WONT_SHARE = {"can't": 0.75, "won't": 0.25}
# Other clitics: the clitic count is split over the contractions by the frequency of their base word.
EN_CLITIC_CONTRACTIONS = {
    "'m": ["i'm"],
    "'re": ["you're", "we're", "they're", "who're"],
    "'ll": ["i'll", "you'll", "he'll", "she'll", "we'll", "they'll", "it'll", "that'll", "there'll"],
    "'ve": ["i've", "you've", "we've", "they've", "would've", "could've", "should've", "might've"],
    "'d": ["i'd", "you'd", "he'd", "she'd", "we'd", "they'd", "it'd"],
    "'s": ["it's", "that's", "he's", "she's", "what's", "there's", "here's", "who's", "where's", "let's", "how's"],
}
# Part of "'s" is the possessive, which is not a word of its own.
EN_S_CONTRACTION_SHARE = 0.7
# Apostrophe-less forms that are not words: their count moves to the contraction and they are dropped. Ambiguous
# real words (cant, wont, ill, id, were, its, well, hell, shell, lets) are deliberately not listed.
EN_APOSTROPHE_LESS = {
    "dont": "don't", "im": "i'm", "youre": "you're", "thats": "that's", "didnt": "didn't", "doesnt": "doesn't",
    "isnt": "isn't", "ive": "i've", "wasnt": "wasn't", "couldnt": "couldn't", "wouldnt": "wouldn't",
    "shouldnt": "shouldn't", "havent": "haven't", "arent": "aren't", "hasnt": "hasn't", "werent": "weren't",
    "theyre": "they're", "whats": "what's", "hes": "he's", "shes": "she's", "theres": "there's",
    "youve": "you've", "youll": "you'll", "theyll": "they'll", "theyve": "they've", "wouldve": "would've",
    "couldve": "could've", "shouldve": "should've", "aint": "ain't", "whos": "who's", "heres": "here's",
    "wheres": "where's",
}
EN_SINGLE_LETTERS = {"a", "i", "k", "u", "x"}

# --- Dutch elisions -------------------------------------------------------------------------------------------

# The tokenizer split "z'n" into "z" + "n", so the lone letter carries the elision's count.
NL_ELISION_FROM_LETTER = {"z": "z'n", "m": "m'n", "d": "d'r"}
# "zo'n" split into "zo" + "n", which cannot be separated from "zo"; Tatoeba NL has zo'n/zo = 246/3158.
NL_ZON_SHARE_OF_ZO = 246 / 3158
NL_SINGLE_LETTERS = {"u"}


def fetch(url: str, target: Path, sha256: str) -> Path:
    if not target.exists():
        print(f"downloading {url}")
        urllib.request.urlretrieve(url, target)
    # Also checks a cached copy: a file from an older, unpinned run must not slip into the build.
    actual = hashlib.sha256(target.read_bytes()).hexdigest()
    if actual != sha256:
        raise SystemExit(f"{target} has sha256 {actual}, expected {sha256}; delete it to download it again")
    return target


def load_counts(path: Path) -> dict[str, int]:
    counts: dict[str, int] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        cut = max(line.rfind(" "), line.rfind("\t"))
        if cut <= 0:
            continue
        word = line[:cut].strip().replace("’", "'").lower()
        try:
            count = int(line[cut + 1:])
        except ValueError:
            continue
        if word and count > counts.get(word, 0):
            counts[word] = max(count, 1)
    return counts


def load_removals(language: str) -> set[str]:
    path = ASSETS / "removals" / f"{language}.txt"
    if not path.exists():
        return set()
    return {line.strip().lower() for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.startswith("#")}


def scowl_words(cache: Path) -> set[str]:
    archive = fetch(SCOWL_URL, cache / "scowl-2020.12.07.tar.gz", SCOWL_SHA256)
    words: set[str] = set()
    pattern = re.compile(r"final/(english|american|british)-(words|contractions|upper|proper-names)\.(\d+)$")
    with tarfile.open(archive) as tar:
        for member in tar.getmembers():
            match = pattern.search(member.name)
            if not match:
                continue
            # Ordinary words up to size 70 (a typical spell checker); names and capitalized words up to 95, so
            # names such as Oleg or Sami stay known and are not "corrected" into common words.
            is_name_list = match.group(2) in ("upper", "proper-names")
            if int(match.group(3)) > (95 if is_name_list else 70):
                continue
            data = tar.extractfile(member).read().decode("latin-1")
            words.update(w.strip().lower() for w in data.splitlines() if w.strip())
    return words


def opentaal_words(cache: Path) -> set[str]:
    path = fetch(OPENTAAL_URL, cache / "opentaal-wordlist-08879a9c.txt", OPENTAAL_SHA256)
    return {w.strip().lower() for w in path.read_text(encoding="utf-8").splitlines() if w.strip()}


def deletes(word: str) -> set[str]:
    return {word[:i] + word[i + 1:] for i in range(len(word))}


def one_edit_neighbors(word: str, delete_index: dict[str, list[str]], counts: dict[str, int]) -> set[str]:
    """Words within one insertion, omission, substitution or transposition of [word]."""
    found = set(delete_index.get(word, []))
    for d in deletes(word):
        if d in counts:
            found.add(d)
        found.update(delete_index.get(d, []))
    found.discard(word)
    return found


def looks_like_misspelling(word: str, count: int, delete_index: dict[str, list[str]], counts: dict[str, int]) -> bool:
    return any(counts[n] >= MISSPELLING_RATIO * count for n in one_edit_neighbors(word, delete_index, counts))


def keep(counts: dict[str, int], valid: set[str], removals: set[str], single_letters: set[str]) -> dict[str, int]:
    ranked = sorted(counts.items(), key=lambda kv: -kv[1])
    top = {word for word, _ in ranked[:KEEP_TOP_RANK]}
    unlisted_pool = {word for word, _ in ranked[:KEEP_UNLISTED_RANK]}
    delete_index: dict[str, list[str]] = {}
    for word in counts:
        for d in deletes(word):
            delete_index.setdefault(d, []).append(word)
    kept = {}
    for word, count in counts.items():
        if word in removals or "`" in word or "�" in word:
            continue
        if len(word) == 1 and word not in single_letters:
            continue
        if word in valid or word in top:
            kept[word] = count
        elif word in unlisted_pool and word.isalpha() and not looks_like_misspelling(word, count, delete_index, counts):
            kept[word] = count
    return kept


def build_en(counts: dict[str, int], valid: set[str], removals: set[str]) -> dict[str, int]:
    words = keep(counts, valid, removals, EN_SINGLE_LETTERS)
    added: dict[str, float] = {}
    nt_from_fragments = 0
    for fragment, contraction in EN_NT_FRAGMENTS.items():
        count = counts.get(fragment, 0)
        added[contraction] = added.get(contraction, 0) + count
        nt_from_fragments += count
        words.pop(fragment, None)
        if fragment in EN_REAL_FRAGMENT_COUNT:
            words[fragment] = EN_REAL_FRAGMENT_COUNT[fragment]
    rest = max(counts.get("'t", 0) - nt_from_fragments, 0)
    for contraction, share in EN_CANT_WONT_SHARE.items():
        added[contraction] = added.get(contraction, 0) + rest * share
    for clitic, contractions in EN_CLITIC_CONTRACTIONS.items():
        total = counts.get(clitic, 0) * (EN_S_CONTRACTION_SHARE if clitic == "'s" else 1.0)
        bases = {c: counts.get(c.split("'")[0], 1) for c in contractions}
        weight = sum(bases.values())
        for contraction, base in bases.items():
            added[contraction] = added.get(contraction, 0) + total * base / weight
    # Backtick forms ("don`t") and apostrophe-less forms that are not words fold into the contraction.
    for word, count in counts.items():
        if "`" in word:
            normal = word.replace("`", "'")
            if normal in added:
                added[normal] += count
    for bare, contraction in EN_APOSTROPHE_LESS.items():
        added[contraction] = added.get(contraction, 0) + counts.get(bare, 0)
        words.pop(bare, None)
    for clitic in EN_CLITIC_CONTRACTIONS:
        words.pop(clitic, None)
    words.pop("'t", None)
    for contraction, count in added.items():
        if count >= 1:
            words[contraction] = max(words.get(contraction, 0), int(round(count)))
    return words


def build_nl(counts: dict[str, int], valid: set[str], removals: set[str]) -> dict[str, int]:
    words = keep(counts, valid, removals, NL_SINGLE_LETTERS)
    for letter, elision in NL_ELISION_FROM_LETTER.items():
        words[elision] = max(words.get(elision, 0), counts.get(letter, 0))
    words["zo'n"] = max(words.get("zo'n", 0), int(round(counts.get("zo", 0) * NL_ZON_SHARE_OF_ZO)))
    for bare in ("zn", "mn", "dr"):
        words.pop(bare, None)
    return words


def write(language: str, words: dict[str, int], sources: list[str]) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    lines = [HEADER, f"# language={language} words={len(words)}"]
    lines += [f"# source: {s}" for s in sources]
    for word, count in sorted(words.items(), key=lambda kv: (-kv[1], kv[0])):
        lines.append(f"{word}\t{round(100 * math.log(max(count, 1)))}")
    (OUT_DIR / f"{language}.txt").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"{language}: {len(words)} words")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, default=Path.home() / ".cache" / "ownkey-dictionary-build")
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    freq = ASSETS / "frequencywords"

    en_counts = load_counts(freq / "en_50k.txt")
    en = build_en(en_counts, scowl_words(args.cache), load_removals("en"))
    write("en", en, [
        "FrequencyWords 2018 en_50k (hermitdave, CC BY-SA 4.0)",
        "SCOWL 2020.12.07 sizes 10-70, names 10-95 (Kevin Atkinson, see SCOWL Copyright) as validity list",
    ])

    nl_counts = load_counts(freq / "nl_50k.txt")
    nl = build_nl(nl_counts, opentaal_words(args.cache), load_removals("nl"))
    write("nl", nl, [
        "FrequencyWords 2018 nl_50k (hermitdave, CC BY-SA 4.0)",
        "OpenTaal wordlist (BSD-3-Clause or CC BY 3.0) as validity list",
    ])


if __name__ == "__main__":
    main()
