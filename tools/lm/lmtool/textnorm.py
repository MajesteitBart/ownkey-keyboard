"""Text normalisation and tokenisation shared by the pipeline, the evaluator and the fixtures.

Rules (mirrored by NgramText.kt in lib/prediction, package org.florisboard.lib.prediction.ngram):
- NFKC, curly/back quotes normalised to a straight apostrophe.
- A word is a run of Unicode letters with internal apostrophes ("don't", "m'n").
  Leading/trailing apostrophes are stripped ("'t" -> "t", "dogs'" -> "dogs").
- Any run of word characters that contains a digit or underscore is dropped entirely ("mp3", "4x4").
- Hyphens and all other punctuation separate words.
- Sentence boundaries (line start, . ! ? and an ellipsis/dialog dash followed by an upper-case letter)
  start a new segment; each segment is later prefixed with the <s> context token.
"""

from __future__ import annotations

import re
import unicodedata

BOS = "<s>"
UNK = "<unk>"

_APOS = str.maketrans({"’": "'", "‘": "'", "ʼ": "'", "`": "'", "´": "'", "′": "'"})

# Runs of word characters and apostrophes. \w covers letters, digits, underscore (and marks after NFKC
# composition are rare). Filtering of digits happens per run.
_RUN = re.compile(r"[\w']+")
_WORD = re.compile(r"^[^\W\d_]+(?:'[^\W\d_]+)*$")
_HAS_DIGIT_OR_US = re.compile(r"[\d_]")

# Subtitle noise.
_TAG = re.compile(r"<[^<>]{0,60}>|\{[^{}]{0,80}\}")
_BRACKETS = re.compile(r"\[[^\[\]]{0,120}\]|\([^()]{0,120}\)")
_SPEAKER = re.compile(r"^\s*(?:[A-Z][A-Z0-9 .'\-]{1,24}|[A-Z][a-z]{1,15}(?: [A-Z][a-z]{1,15})?)\s*:\s+(?=\S)")
_LEAD_DASH = re.compile(r"^\s*[-‐-―]+\s*")
_CREDIT = re.compile(
    r"(?i)(ondertitel|subtitl|\bsubs?\b|\bsync|vertaling:|vertaald door|ripped|geript|www\.|https?:|\.com\b|"
    r"\.nl\b|\.org\b|\bdvd\b|divx|xvid|bluray|\b720p|\b1080p|x264|opensubtitles|addic7ed|caption|transcript)"
)
_MUSIC = re.compile(r"[♪♫♬♯#]")

# Sentence splitting: terminator(s), whitespace, optional opening quotes/dashes, then the next char.
_SPLIT = re.compile(r"([.!?…]+)(['\"“”»«)\]]*)\s+(?:[-‐-―]+\s*)?(?=(\S))")
# Dialog dash in the middle of a line followed by an upper-case letter.
_MID_DASH = re.compile(r"\s[-‐-―]+\s+(?=[^\W\d_])")

ABBREVIATIONS = {
    "en": {"mr", "mrs", "ms", "dr", "st", "jr", "sr", "mt", "vs", "prof", "gen", "col", "lt", "sgt", "capt",
           "cpt", "rev", "hon", "gov", "sen", "rep", "messrs", "no", "etc", "approx", "dept", "inc", "ltd",
           "co", "corp", "ave", "fig", "e.g", "i.e"},
    "nl": {"dhr", "mevr", "mw", "mr", "dr", "drs", "ir", "ing", "prof", "st", "bijv", "bv", "enz", "etc",
           "o.a", "m.a.w", "d.w.z", "i.p.v", "z.g.a.n", "nr", "blz", "ca", "jl", "jhr", "mej", "wed", "hr",
           "sr", "jr", "v.d", "vs", "gen", "kol", "lt", "sgt", "kpt", "ds", "pt", "a.u.b", "s.v.p", "t.o.v"},
}


def nfkc(text: str) -> str:
    return unicodedata.normalize("NFKC", text).translate(_APOS)


def fold(word: str) -> str:
    """Folded key used for prefix matching: strip combining marks and apostrophes (input is lower case)."""
    decomposed = unicodedata.normalize("NFD", word)
    return "".join(c for c in decomposed if c != "'" and unicodedata.category(c) != "Mn")


def word_tokens(segment: str) -> list[str]:
    """Original-case word tokens of a text segment (no sentence splitting)."""
    out = []
    for run in _RUN.findall(segment):
        if _HAS_DIGIT_OR_US.search(run):
            continue
        run = run.strip("'")
        if not run:
            continue
        if "''" in run:
            parts = [p.strip("'") for p in run.split("''")]
        else:
            parts = [run]
        for p in parts:
            if p and _WORD.match(p):
                out.append(p)
    return out


def split_sentences(text: str, lang: str) -> list[str]:
    """Split a line into sentence segments."""
    abbrev = ABBREVIATIONS.get(lang, set())
    text = _MID_DASH.sub(" \x00", text) if "-" in text or "–" in text or "—" in text else text
    pieces: list[str] = []
    start = 0
    for m in _SPLIT.finditer(text):
        term = m.group(1)
        nxt = m.group(3)
        if "." in term and len(term) == 1:
            # Abbreviation or initial: no boundary.
            before = text[start:m.start(1)]
            mw = re.search(r"([\w.]+)$", before)
            last = mw.group(1).lower() if mw else ""
            if last in abbrev or (len(last) == 1 and last.isalpha()):
                continue
        elif term.startswith("..") or "…" in term:
            if not nxt.isupper():
                continue
        pieces.append(text[start:m.end(2)])
        start = m.end(2)
    pieces.append(text[start:])
    out: list[str] = []
    for p in pieces:
        out.extend(p.split("\x00"))
    return out


def clean_subtitle_line(line: str) -> str | None:
    """Remove subtitle noise; returns None when the whole line should be dropped."""
    if _MUSIC.search(line):
        return None
    if _CREDIT.search(line):
        return None
    line = _TAG.sub(" ", line)
    line = _BRACKETS.sub(" ", line)
    line = _SPEAKER.sub("", line, count=1)
    line = _LEAD_DASH.sub("", line)
    return line


def segments_original_case(line: str, lang: str, subtitle: bool) -> list[list[str]]:
    """Normalise one raw line into sentence segments of original-case tokens."""
    line = nfkc(line)
    if subtitle:
        cleaned = clean_subtitle_line(line)
        if cleaned is None:
            return []
        line = cleaned
    segs = []
    for seg in split_sentences(line, lang):
        toks = word_tokens(seg)
        if toks:
            segs.append(toks)
    return segs


def normalize_word(word: str) -> str:
    """Lower-case model token for an already extracted word."""
    return nfkc(word).lower().strip("'")


def tokenize_lower(text: str, lang: str = "en") -> list[str]:
    """Tokenise free text to lower-case model tokens with <s> at each sentence start."""
    out: list[str] = []
    for seg in segments_original_case(text, lang, subtitle=False):
        out.append(BOS)
        out.extend(t.lower() for t in seg)
    return out
