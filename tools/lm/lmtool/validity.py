"""Word validity: which vocabulary entries are really words, as opposed to tokens that only occur in the training text.

The vocabulary is frequency-based and drawn from subtitles and web text, so it also holds typos ("mischien"), OCR
debris, truncations ("iet") and stray foreign tokens. Autocorrect must not treat those as trusted words. A word is
*validated* when, after normalisation (lower case, straight apostrophe, edge apostrophes stripped: the model token
rules of textnorm), at least one of these holds:

1. it is in a curated word list of the language (OpenTaal for Dutch, SCOWL/ESDB for English; see tools/lm/SOURCES.md);
2. the model writes it with capitals (the CASE flag: names, places, brands, acronyms);
3. it is frequent enough in the training text to be an intentional word of its own, at least ``min_ppm`` occurrences
   per million tokens (chat abbreviations, interjections and loanwords that no word list carries: "lol", "okee").

Words that the list carries in lower case are also marked *listed* (dictionary words): real-word autocorrect may
replace a rare dictionary word that the context rejects ("den" -> "doen"), but not a name, an acronym (capitalised
list entries such as "Ty" or "NP" validate their word without listing it) or a chat word that is only valid by rules
2 or 3.

Curated list entries are tokenised like training text: hyphens, spaces and other punctuation split them, so "e-mail"
validates "e" and "mail", "ad hoc" validates "ad" and "hoc", "dr." validates "dr"; parts with digits are dropped.
Stored as the two bit sets of the VALD section of format version 2 (tools/lm/FORMAT.md).
"""

from __future__ import annotations

import json
import os

import numpy as np

from . import textnorm

# SCOWL/ESDB lists made with app.aspell.net carry a licence header that ends with this line.
_HEADER_END = "---"


def load_wordlist(path: str, lower_only: bool = False) -> set[str]:
    """Normalised model tokens of a word list (one entry per line; '#' comments and a SCOWL header are skipped). With
    [lower_only], only tokens written in lower case in the list (dictionary words rather than names or acronyms)."""
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()
    if lines and lines[0].startswith("Custom wordlist") and _HEADER_END in lines:
        lines = lines[lines.index(_HEADER_END) + 1:]
    out: set[str] = set()
    for line in lines:
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        for tok in textnorm.word_tokens(textnorm.nfkc(line)):
            w = textnorm.normalize_word(tok)
            if w and (not lower_only or w == textnorm.nfkc(tok).strip("'")):
                out.add(w)
    return out


def load_wordlists(paths: list[str], lower_only: bool = False) -> set[str]:
    out: set[str] = set()
    for p in paths:
        out |= load_wordlist(p, lower_only)
    return out


def validity(words: list[str], counts: np.ndarray, casing: dict, wordlist: set[str], min_ppm: float,
             lower_wordlist: set[str] | None = None) -> tuple[np.ndarray, np.ndarray, dict]:
    """(valid, listed) boolean flags per vocabulary id (id 0 = <s> is False in both) and statistics; see the module
    documentation. [wordlist] holds every list token, [lower_wordlist] the lower-case ones (default: all)."""
    v = len(words)
    counts = np.asarray(counts[:v], dtype=np.int64)
    total = float(counts[1:].sum()) or 1.0
    in_list = np.array([i > 0 and w in wordlist for i, w in enumerate(words)], dtype=bool)
    in_list[0] = False
    lower = wordlist if lower_wordlist is None else lower_wordlist
    listed = np.array([i > 0 and w in lower for i, w in enumerate(words)], dtype=bool)
    listed[0] = False
    cased = np.array([i > 0 and w in casing for i, w in enumerate(words)], dtype=bool)
    min_count = min_ppm * total / 1e6
    frequent = counts >= min_count
    frequent[0] = False
    valid = in_list | cased | frequent
    valid[0] = False
    junk = ~valid
    junk[0] = False
    stats = {
        "min_ppm": min_ppm, "min_count": int(np.ceil(min_count)), "in_list": int(in_list.sum()),
        "listed": int(listed.sum()), "capitalized": int(cased.sum()),
        "frequent_only": int((frequent & ~in_list & ~cased).sum()),
        "valid": int(valid.sum()), "not_valid": int(junk.sum()),
        "not_valid_token_share": float(counts[junk].sum() / total),
    }
    return valid, listed, stats


def validity_from_counts(count_dir: str, wordlist_paths: list[str],
                         min_ppm: float) -> tuple[np.ndarray, np.ndarray, dict]:
    with open(os.path.join(count_dir, "vocab.txt"), encoding="utf-8") as f:
        words = [line.rstrip("\n") for line in f]
    with open(os.path.join(count_dir, "casing.json"), encoding="utf-8") as f:
        casing = json.load(f)
    counts = np.load(os.path.join(count_dir, "uni.c.npy"))
    wordlist = load_wordlists(wordlist_paths)
    lower = load_wordlists(wordlist_paths, lower_only=True)
    valid, listed, stats = validity(words, counts, casing, wordlist, min_ppm, lower)
    stats["wordlist_entries"] = len(wordlist)
    stats["wordlists"] = [os.path.basename(p) for p in wordlist_paths]
    return valid, listed, stats
