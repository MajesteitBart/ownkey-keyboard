#!/usr/bin/env python3
"""Build and validate the kbbench sentence corpus (dev.jsonl, test.jsonl).

Stdlib only and deterministic. Usage, from the repository root:

    python3 -I tools/kbbench/corpus/build_corpus.py           # rebuild both splits, then validate
    python3 -I tools/kbbench/corpus/build_corpus.py --check   # validate the existing files only

Authored sentences come from authored_{en,nl,mix}.txt. The Tatoeba part is sampled from the
per-language exports in raw/ (downloaded when missing): eligible sentences are sorted by
sha1(sentence id) and taken in that order, skipping near-duplicates; each id's split is fixed
by the low bit of sha1("split:" + id), so dev and test are disjoint by construction.
See README.md and ../PROTOCOL.md for the rules this script enforces.
"""

from __future__ import annotations

import argparse
import bz2
import hashlib
import heapq
import json
import re
import sys
import unicodedata
import urllib.request
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
RAW_DIR = HERE / "raw"

SPLITS = ("dev", "test")
LANGS = ("en", "nl", "mix")
AUTHORED_PER_SPLIT = {"en": 100, "nl": 100, "mix": 40}
TATOEBA_PER_SPLIT = {"en": 50, "nl": 50, "mix": 0}
TATOEBA_EXPORTS = {
    "en": ("eng", "https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences.tsv.bz2"),
    "nl": ("nld", "https://downloads.tatoeba.org/exports/per_language/nld/nld_sentences.tsv.bz2"),
}

MIN_WORDS, MAX_WORDS = 4, 16
TATOEBA_MIN_WORDS = 5
MIX_MIN_TERMS, MIX_MAX_TERMS = 1, 3
CANDIDATE_POOL = 20000  # smallest sha1 keys kept per language while streaming an export
NAME_CAP_PER_SPLIT = 8  # Tatoeba is full of "Tom" and "Mary"; cap them per split and language

# Tatoeba sentences skipped after manual review (unnatural, dated, sensitive or awkward to type).
# Adding an id here only moves the sampler on to the next eligible sentence.
TATOEBA_BLOCKLIST: set[str] = {
    "16055",     # "You yourself have to finish it." (stilted)
    "3205063",   # ungrammatical ("tell to do")
    "8409544",   # quoted two-speaker dialogue
    "12678471",  # "best of all Capricorns" (odd)
    "12354360",  # Plautus
    "7566822",   # "Je hoort op een andere planeet." (unidiomatic)
    "12747316",  # two fragments with trailing ellipses
}
# Words marking large topic clusters on Tatoeba (language advocacy, recurring fictional casts).
TATOEBA_TOPIC_BLOCK = {
    "berber", "kabyle", "kabylie", "tamazight", "esperanto", "tatoeba", "sami", "layla", "skura",
    "yanni", "rima", "mennad", "baya", "ziri",
}

# Conversational markers: a Tatoeba sentence must contain at least one first/second person word.
CONVERSATIONAL = {
    "en": {"i", "i'm", "i'll", "i've", "i'd", "me", "my", "mine", "we", "we're", "we'll", "we've",
           "we'd", "us", "our", "let's", "you", "you're", "you'll", "you've", "you'd", "your", "yours"},
    "nl": {"ik", "me", "mij", "mijn", "m'n", "we", "wij", "ons", "onze", "je", "jij", "jou", "jouw",
           "jullie", "u", "uw"},
}
SINGLE_LETTER_OK = {"en": {"a", "i"}, "nl": {"u"}}
NAMES_CAPPED = {"tom", "tom's", "toms", "mary", "mary's", "marys"}
ABBREVIATION_RE = re.compile(
    r"^(mr|mrs|ms|dr|st|jr|sr|prof|mt|etc|vs|bijv|dhr|mevr|mw|nr|ca|evt|enz|bv|resp|jl|blz)\.$", re.I
)

APOSTROPHE_VARIANTS = {"’": "'", "ʼ": "'"}
SENTENCE_PUNCT = '.,!?;:"()“”„…«»'
FORBIDDEN_RE = re.compile(r"[0-9٠-٩\-‐-―−]|https?:|www\.", re.I)
WORD_RE = re.compile(r"[^\W\d_]+(?:'[^\W\d_]+)*")
ID_RE = re.compile(r"^(en|nl|mix)-(dev|test)-(\d{3})$")
SOURCE_RE = re.compile(r"^(authored|tatoeba:\d+)$")
KEYS = ["id", "split", "lang", "source", "text", "words"]


class CorpusError(ValueError):
    pass


# ---------------------------------------------------------------- normalisation


def is_letter(ch: str) -> bool:
    """A Latin letter that folds to a-z (diacritics allowed, e.g. e with acute or diaeresis)."""
    if not ch.isalpha():
        return False
    base = unicodedata.normalize("NFD", ch)
    return (
        base[0].isascii()
        and base[0].isalpha()
        and all(unicodedata.category(c) == "Mn" for c in base[1:])
    )


def normalize_words(text: str) -> list[str]:
    """Return the typist's word list for text, or raise CorpusError when text breaks the protocol."""
    if text != text.strip() or "\t" in text or "\n" in text:
        raise CorpusError("leading/trailing whitespace, tab or newline")
    if unicodedata.normalize("NFC", text) != text:
        raise CorpusError("text is not NFC")
    if FORBIDDEN_RE.search(text):
        raise CorpusError("digit, hyphen/dash or URL")
    for ch in text:
        if not (ch == " " or ch == "'" or ch in APOSTROPHE_VARIANTS or ch in SENTENCE_PUNCT or is_letter(ch)):
            raise CorpusError(f"symbol {ch!r} (U+{ord(ch):04X})")
    words = []
    for raw_tok in text.split(" "):
        tok = raw_tok
        for variant, plain in APOSTROPHE_VARIANTS.items():
            tok = tok.replace(variant, plain)
        tok = tok.strip(SENTENCE_PUNCT)
        if not tok:
            continue
        if not WORD_RE.fullmatch(tok):
            raise CorpusError(f"punctuation or edge apostrophe inside token {raw_tok!r}")
        word = tok.lower()
        if unicodedata.normalize("NFC", word) != word or not all(c == "'" or is_letter(c) for c in word):
            raise CorpusError(f"token {raw_tok!r} does not lowercase cleanly")
        words.append(word)
    return words


def check_word(word: str) -> str | None:
    if not isinstance(word, str) or not word:
        return "empty or non-string word"
    if word != word.lower() or unicodedata.normalize("NFC", word) != word:
        return f"word {word!r} not lowercase NFC"
    if not WORD_RE.fullmatch(word) or not all(c == "'" or is_letter(c) for c in word):
        return f"word {word!r} has symbols"
    return None


def fold(word: str) -> str:
    """Protocol comparison form: no diacritics, no apostrophes."""
    decomposed = unicodedata.normalize("NFD", word)
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn" and c != "'")


def folded(words: list[str]) -> tuple[str, ...]:
    return tuple(fold(w) for w in words)


def levenshtein(a: tuple[str, ...], b: tuple[str, ...]) -> int:
    prev = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        cur = [i]
        for j, y in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (x != y)))
        prev = cur
    return prev[-1]


def near_duplicate(a: tuple[str, ...], b: tuple[str, ...]) -> bool:
    """Same folded words, word-set Jaccard >= 0.8, or a word-level edit distance of 1 (2 for long ones)."""
    if not a or not b:
        return False  # invalid sentence, reported elsewhere
    if a == b:
        return True
    sa, sb = set(a), set(b)
    if len(sa & sb) / len(sa | sb) >= 0.8:
        return True
    longest = max(len(a), len(b))
    limit = 1 if longest < 10 else 2
    if abs(len(a) - len(b)) > limit:
        return False
    shared = sum((Counter(a) & Counter(b)).values())
    if shared < longest - limit:
        return False
    return levenshtein(a, b) <= limit


# ---------------------------------------------------------------- authored sentences


def load_authored() -> dict[str, dict[str, list[dict]]]:
    """authored_<lang>.txt: `split<TAB>text`, mix adds `<TAB>term|term` naming the English insertions."""
    out: dict[str, dict[str, list[dict]]] = {}
    for lang in LANGS:
        path = HERE / f"authored_{lang}.txt"
        per_split: dict[str, list[dict]] = {s: [] for s in SPLITS}
        for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip() or line.startswith("#"):
                continue
            cols = line.split("\t")
            where = f"{path.name}:{lineno}"
            expected_cols = 3 if lang == "mix" else 2
            if len(cols) != expected_cols:
                raise CorpusError(f"{where}: expected {expected_cols} tab-separated columns, got {len(cols)}")
            split, text = cols[0], cols[1]
            if split not in SPLITS:
                raise CorpusError(f"{where}: unknown split {split!r}")
            terms = [t for t in cols[2].split("|")] if lang == "mix" else []
            per_split[split].append({"text": text, "terms": terms, "where": where})
        out[lang] = per_split
    return out


# ---------------------------------------------------------------- Tatoeba


def raw_path(lang: str) -> Path:
    code, url = TATOEBA_EXPORTS[lang]
    return RAW_DIR / url.rsplit("/", 1)[1]


def ensure_raw(download: bool) -> None:
    RAW_DIR.mkdir(exist_ok=True)
    for lang, (_, url) in TATOEBA_EXPORTS.items():
        path = raw_path(lang)
        if path.exists():
            continue
        if not download:
            raise CorpusError(f"missing {path.relative_to(HERE)}; run without --no-download")
        print(f"downloading {url}", file=sys.stderr)
        tmp = path.with_suffix(path.suffix + ".part")
        with urllib.request.urlopen(url, timeout=120) as resp, open(tmp, "wb") as fh:
            while chunk := resp.read(1 << 20):
                fh.write(chunk)
        tmp.rename(path)


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        while chunk := fh.read(1 << 20):
            h.update(chunk)
    return h.hexdigest()


def iter_export(lang: str):
    code, _ = TATOEBA_EXPORTS[lang]
    with bz2.open(raw_path(lang), "rt", encoding="utf-8", newline="\n") as fh:
        for line in fh:
            parts = line.rstrip("\n").split("\t")
            if len(parts) != 3 or parts[1] != code or not parts[0].isdigit():
                continue
            yield parts[0], parts[2]


def clean_tatoeba_text(text: str) -> str:
    return " ".join(unicodedata.normalize("NFC", text).split())


def tatoeba_words(lang: str, sid: str, text: str) -> list[str] | None:
    """Words for an eligible Tatoeba sentence, else None."""
    if sid in TATOEBA_BLOCKLIST:
        return None
    if not re.search(r"[.!?…][\"”»)]*$", text):
        return None
    try:
        words = normalize_words(text)
    except CorpusError:
        return None
    if not (TATOEBA_MIN_WORDS <= len(words) <= MAX_WORDS):
        return None
    if not CONVERSATIONAL[lang] & set(words):
        return None
    if TATOEBA_TOPIC_BLOCK & {w.split("'")[0] for w in words}:
        return None
    for raw_tok in text.split(" "):
        if ABBREVIATION_RE.match(raw_tok.strip(SENTENCE_PUNCT.replace(".", ""))):
            return None
        core = raw_tok.strip(SENTENCE_PUNCT)
        if len(core) >= 2 and core.isupper() and core != "OK":
            return None
    if any(len(w) == 1 and w not in SINGLE_LETTER_OK[lang] for w in words):
        return None
    return words


def select_tatoeba(authored_folded: list[tuple[str, ...]]) -> dict[str, dict[str, list[dict]]]:
    picks: dict[str, dict[str, list[dict]]] = {}
    accepted_folded = list(authored_folded)
    for lang in ("en", "nl"):
        need = TATOEBA_PER_SPLIT[lang]
        heap: list[tuple[int, str, str, list[str]]] = []
        for sid, raw_text in iter_export(lang):
            text = clean_tatoeba_text(raw_text)
            words = tatoeba_words(lang, sid, text)
            if words is None:
                continue
            key = int(hashlib.sha1(sid.encode("ascii")).hexdigest(), 16)
            item = (-key, sid, text, words)
            if len(heap) < CANDIDATE_POOL:
                heapq.heappush(heap, item)
            elif item > heap[0]:
                heapq.heapreplace(heap, item)
        candidates = sorted(heap, reverse=True)  # ascending sha1
        chosen: dict[str, list[dict]] = {s: [] for s in SPLITS}
        names = {s: 0 for s in SPLITS}
        for _, sid, text, words in candidates:
            if all(len(chosen[s]) >= need for s in SPLITS):
                break
            # The split depends only on the sentence id, so blocklisting one id never moves another.
            split = SPLITS[int(hashlib.sha1(f"split:{sid}".encode("ascii")).hexdigest(), 16) & 1]
            if len(chosen[split]) >= need:
                continue
            fw = folded(words)
            if any(near_duplicate(fw, other) for other in accepted_folded):
                continue
            has_name = bool(NAMES_CAPPED & set(words))
            if has_name and names[split] >= NAME_CAP_PER_SPLIT:
                continue
            names[split] += has_name
            chosen[split].append({"sid": sid, "text": text, "words": words})
            accepted_folded.append(fw)
        for s in SPLITS:
            if len(chosen[s]) < need:
                raise CorpusError(f"only {len(chosen[s])} eligible Tatoeba {lang} sentences for {s}")
        picks[lang] = chosen
    return picks


# ---------------------------------------------------------------- assemble and write


def words_or_empty(text: str) -> list[str]:
    try:
        return normalize_words(text)
    except CorpusError:
        return []  # validate() reports the reason


def assemble(authored, tatoeba) -> dict[str, list[dict]]:
    out: dict[str, list[dict]] = {s: [] for s in SPLITS}
    for split in SPLITS:
        for lang in LANGS:
            n = 0
            for entry in authored[lang][split]:
                n += 1
                out[split].append({
                    "id": f"{lang}-{split}-{n:03d}", "split": split, "lang": lang, "source": "authored",
                    "text": entry["text"], "words": words_or_empty(entry["text"]),
                })
            for entry in tatoeba.get(lang, {}).get(split, []):
                n += 1
                out[split].append({
                    "id": f"{lang}-{split}-{n:03d}", "split": split, "lang": lang,
                    "source": f"tatoeba:{entry['sid']}", "text": entry["text"], "words": entry["words"],
                })
    return out


def write_jsonl(records: dict[str, list[dict]]) -> None:
    for split in SPLITS:
        path = HERE / f"{split}.jsonl"
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            for rec in records[split]:
                fh.write(json.dumps({k: rec[k] for k in KEYS}, ensure_ascii=False) + "\n")


def read_jsonl() -> dict[str, list[dict]]:
    out: dict[str, list[dict]] = {}
    for split in SPLITS:
        path = HERE / f"{split}.jsonl"
        rows = []
        for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError as exc:
                raise CorpusError(f"{path.name}:{lineno}: invalid JSON: {exc}") from exc
        out[split] = rows
    return out


# ---------------------------------------------------------------- validation


def validate(records, authored, raw_texts: dict[str, str] | None) -> list[str]:
    errors: list[str] = []
    all_rows: list[tuple[str, dict, tuple[str, ...]]] = []
    seen_ids: set[str] = set()

    # Authored files: per split counts, constraints, mix term annotations.
    for lang in LANGS:
        for split in SPLITS:
            entries = authored[lang][split]
            if len(entries) != AUTHORED_PER_SPLIT[lang]:
                errors.append(f"authored_{lang}.txt: {len(entries)} {split} sentences, need {AUTHORED_PER_SPLIT[lang]}")
            for entry in entries:
                try:
                    words = normalize_words(entry["text"])
                except CorpusError as exc:
                    errors.append(f"{entry['where']}: {exc}")
                    continue
                if not (MIN_WORDS <= len(words) <= MAX_WORDS):
                    errors.append(f"{entry['where']}: {len(words)} words, need {MIN_WORDS}-{MAX_WORDS}")
                if lang == "mix":
                    terms = entry["terms"]
                    if not (MIX_MIN_TERMS <= len(terms) <= MIX_MAX_TERMS):
                        errors.append(f"{entry['where']}: {len(terms)} English terms, need {MIX_MIN_TERMS}-{MIX_MAX_TERMS}")
                    joined = " " + " ".join(words) + " "
                    for term in terms:
                        try:
                            tw = normalize_words(term)
                        except CorpusError as exc:
                            errors.append(f"{entry['where']}: term {term!r}: {exc}")
                            continue
                        if not tw or f" {' '.join(tw)} " not in joined:
                            errors.append(f"{entry['where']}: English term {term!r} not found in sentence")

    for split in SPLITS:
        rows = records[split]
        counts = Counter()
        authored_by_lang: dict[str, list[str]] = {lang: [] for lang in LANGS}
        for idx, rec in enumerate(rows, 1):
            where = f"{split}.jsonl:{idx}"
            if not isinstance(rec, dict) or list(rec.keys()) != KEYS:
                errors.append(f"{where}: keys must be exactly {KEYS} in that order")
                continue
            rid, rsplit, lang, source, text, words = (rec[k] for k in KEYS)
            m = ID_RE.match(rid) if isinstance(rid, str) else None
            if not m:
                errors.append(f"{where}: bad id {rid!r}")
                continue
            if rid in seen_ids:
                errors.append(f"{where}: duplicate id {rid}")
            seen_ids.add(rid)
            if rsplit != split or m.group(2) != split:
                errors.append(f"{where}: split mismatch for {rid}")
            if lang not in LANGS or m.group(1) != lang:
                errors.append(f"{where}: lang mismatch for {rid}")
            if not isinstance(source, str) or not SOURCE_RE.match(source):
                errors.append(f"{where}: bad source {source!r}")
                continue
            if lang == "mix" and source != "authored":
                errors.append(f"{where}: mix sentences must be authored")
            if not isinstance(text, str) or not isinstance(words, list):
                errors.append(f"{where}: text must be a string and words a list")
                continue
            for w in words:
                problem = check_word(w)
                if problem:
                    errors.append(f"{where}: {problem}")
            try:
                expected = normalize_words(text)
            except CorpusError as exc:
                errors.append(f"{where} ({rid}): {exc}")
                continue
            if expected != words:
                errors.append(f"{where} ({rid}): words do not match normalized text: {expected}")
            low = TATOEBA_MIN_WORDS if source != "authored" else MIN_WORDS
            if not (low <= len(words) <= MAX_WORDS):
                errors.append(f"{where} ({rid}): {len(words)} words, need {low}-{MAX_WORDS}")
            kind = "authored" if source == "authored" else "tatoeba"
            counts[(lang, kind)] += 1
            if kind == "authored":
                authored_by_lang[lang].append(text)
            elif raw_texts is not None:
                sid = source.split(":", 1)[1]
                raw = raw_texts.get(sid)
                if raw is None:
                    errors.append(f"{where} ({rid}): Tatoeba sentence {sid} not found in raw {lang} export")
                elif clean_tatoeba_text(raw) != text:
                    errors.append(f"{where} ({rid}): text differs from Tatoeba sentence {sid}")
            all_rows.append((split, rec, folded(words)))
        for lang in LANGS:
            for kind, need in (("authored", AUTHORED_PER_SPLIT[lang]), ("tatoeba", TATOEBA_PER_SPLIT[lang])):
                if counts[(lang, kind)] != need:
                    errors.append(f"{split}.jsonl: {counts[(lang, kind)]} {lang}/{kind}, need {need}")
            want = [e["text"] for e in authored[lang][split]]
            if authored_by_lang[lang] != want:
                errors.append(f"{split}.jsonl: {lang} authored sentences differ from authored_{lang}.txt (rebuild)")

    # Uniqueness and dev/test disjointness, on folded words.
    for i in range(len(all_rows)):
        si, ri, fi = all_rows[i]
        for j in range(i + 1, len(all_rows)):
            sj, rj, fj = all_rows[j]
            if near_duplicate(fi, fj):
                scope = "dev/test overlap" if si != sj else "duplicate within split"
                errors.append(f"{scope}: {ri['id']} {ri['text']!r} ~ {rj['id']} {rj['text']!r}")
    return errors


def load_raw_texts(records) -> dict[str, str] | None:
    need = {
        rec["source"].split(":", 1)[1]
        for rows in records.values() for rec in rows
        if isinstance(rec, dict) and str(rec.get("source", "")).startswith("tatoeba:")
    }
    if not all(raw_path(lang).exists() for lang in TATOEBA_EXPORTS):
        return None
    found: dict[str, str] = {}
    for lang in TATOEBA_EXPORTS:
        for sid, text in iter_export(lang):
            if sid in need:
                found[sid] = text
    return found


def report(records) -> None:
    for split in SPLITS:
        rows = records[split]
        by = Counter((r["lang"], "authored" if r["source"] == "authored" else "tatoeba") for r in rows)
        mean = sum(len(r["words"]) for r in rows) / len(rows)
        parts = ", ".join(f"{lang} {by[(lang, 'authored')]}+{by[(lang, 'tatoeba')]}" for lang in LANGS)
        print(f"{split}: {len(rows)} sentences (authored+tatoeba: {parts}), mean {mean:.2f} words")
        for lang in LANGS:
            sel = [r for r in rows if r["lang"] == lang]
            for kind in ("authored", "tatoeba"):
                sub = [r for r in sel if (r["source"] == "authored") == (kind == "authored")]
                if sub:
                    m = sum(len(r["words"]) for r in sub) / len(sub)
                    print(f"  {lang:<3} {kind:<8} n={len(sub):<3} mean_words={m:.2f}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    ap.add_argument("--check", action="store_true", help="validate dev/test.jsonl without rebuilding")
    ap.add_argument("--no-download", action="store_true", help="fail instead of downloading missing exports")
    args = ap.parse_args()
    try:
        authored = load_authored()
        if not args.check:
            ensure_raw(download=not args.no_download)
            for lang in TATOEBA_EXPORTS:
                print(f"{raw_path(lang).relative_to(HERE)} sha256={sha256_file(raw_path(lang))}")
            authored_folded = [
                folded(words_or_empty(entry["text"]))
                for lang in LANGS for split in SPLITS for entry in authored[lang][split]
            ]
            tatoeba = select_tatoeba(authored_folded)
            write_jsonl(assemble(authored, tatoeba))
        records = read_jsonl()
        raw_texts = load_raw_texts(records)
        if raw_texts is None:
            print("raw Tatoeba exports not present: skipping source text cross-check", file=sys.stderr)
        errors = validate(records, authored, raw_texts)
    except (CorpusError, OSError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    report(records)
    if errors:
        for e in errors:
            print(f"FAIL {e}", file=sys.stderr)
        print(f"{len(errors)} validation error(s)", file=sys.stderr)
        return 1
    print("validation passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
