"""Stage 2: prepared token files -> vocabulary, casing flags and partitioned n-gram counts.

Pass 1 (parallel): exclusion filtering, unigram counts, casing statistics.
Pass 2 (parallel): map tokens to ids, count unigrams/bigrams/trigrams with numpy per chunk, write per-partition
chunk files (partition = id of the word just before the predicted word, so every later KN step is partition-local
except the unigram continuation counts, which are V-sized vectors).

Id space while counting: 0 = <s>, 1..V-1 = vocabulary (sorted by folded key, then word, as UTF-8 bytes), V = <unk>.
Keys: trigram = u<<2b | v<<b | w, bigram = v<<b | w with b = id bits (18 by default, ids < 2^18; more when the
vocabulary needs it). counts.json records the id bits and the partition count; lmtool.kn reads them from there.
"""

from __future__ import annotations

import json
import multiprocessing as mp
import os
import sys
import time
from collections import Counter

import numpy as np

from . import textnorm

ID_BITS = 18
ID_MASK = (1 << ID_BITS) - 1
NPART = 16
BLOCK_LINES = 200


def layout(count_dir: str) -> tuple[int, int]:
    """(id bits, partition count) of a count directory (counts.json; older directories use the defaults)."""
    try:
        with open(os.path.join(count_dir, "counts.json")) as f:
            meta = json.load(f)
    except FileNotFoundError:
        meta = {}
    return int(meta.get("id_bits", ID_BITS)), int(meta.get("partitions", NPART))

# --------------------------------------------------------------------------------------------------------------
# Chunking


def file_chunks(paths: list[str], chunk_bytes: int) -> list[tuple[str, int, int]]:
    out = []
    for p in paths:
        size = os.path.getsize(p)
        with open(p, "rb") as f:
            start = 0
            while start < size:
                end = min(size, start + chunk_bytes)
                if end < size:
                    f.seek(end)
                    f.readline()
                    end = f.tell()
                out.append((p, start, end))
                start = end
    return out


def read_chunk(path: str, start: int, end: int) -> list[str]:
    with open(path, "rb") as f:
        f.seek(start)
        data = f.read(end - start)
    return data.decode("utf-8").splitlines()


# --------------------------------------------------------------------------------------------------------------
# Exclusion set (exact match on folded token sequences of benchmark sentences)


def fold_text(s: str) -> str:
    if s.isascii():
        return s.replace("'", "")
    return textnorm.fold(s)


def load_exclusions(paths: list[str]) -> set[str]:
    out: set[str] = set()
    for p in paths:
        if not os.path.exists(p):
            continue
        with open(p, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                rec = json.loads(line)
                words = [textnorm.normalize_word(w) for w in rec.get("words", [])]
                if words:
                    out.add(fold_text(" ".join(words)))
    return out


_EXCL: set[str] = set()
_VOCAB: dict[str, int] = {}
_UNK = 0
_OUTDIR = ""
_BLOCK: dict[str, int] = {}  # prepared file -> lines per dispersion block (default BLOCK_LINES)
_ID_BITS = ID_BITS
_NPART = NPART


def _excluded(segs_lower: list[str]) -> bool:
    if not _EXCL:
        return False
    flat = fold_text(" ".join(segs_lower))
    if flat in _EXCL:
        return True
    if len(segs_lower) > 1:
        for s in segs_lower:
            if fold_text(s) in _EXCL:
                return True
    return False


def _shouting(line: str) -> bool:
    up = sum(1 for c in line if c.isupper())
    if up < 4:
        return False
    low = sum(1 for c in line if c.islower())
    return up > 2 * low


# --------------------------------------------------------------------------------------------------------------
# Pass 1


def _pass1(job):
    path, start, end = job
    uni: Counter = Counter()
    init: Counter = Counter()
    noninit: Counter = Counter()
    cased: Counter = Counter()
    df: Counter = Counter()
    block: set = set()
    dropped = 0
    lines = 0
    block_lines = _BLOCK.get(path, BLOCK_LINES)
    for line in read_chunk(path, start, end):
        if not line:
            continue
        lines += 1
        if lines % block_lines == 0:
            df.update(block)
            block = set()
        segs = line.split("\t")
        segs_lower = [s.lower() for s in segs]
        if _excluded(segs_lower):
            dropped += 1
            continue
        shout = _shouting(line)
        for s, sl in zip(segs, segs_lower):
            toks_l = sl.split(" ")
            uni.update(toks_l)
            block.update(toks_l)
            if shout:
                continue
            init[toks_l[0]] += 1
            if len(toks_l) > 1:
                noninit.update(toks_l[1:])
                if s != sl:
                    toks = s.split(" ")
                    for t, tl in zip(toks[1:], toks_l[1:]):
                        if t != tl:
                            cased[t] += 1
    df.update(block)
    return uni, init, noninit, cased, df, dropped, lines


def choose_vocab(uni: Counter, df: Counter, max_size: int, min_count: int, min_df: int) -> list[str]:
    """Top words by count among those with count >= min_count that occur in >= min_df blocks of
    BLOCK_LINES prepared lines (about one film / a few web pages; per file with --block-lines, so that a subtitle
    sample of every line can use 1000-line blocks): drops film-specific names and typos."""
    cands = [(c, w) for w, c in uni.items() if c >= min_count and df[w] >= min_df and len(w) <= 40]
    cands.sort(key=lambda x: (-x[0], x[1]))
    words = [w for _, w in cands[: max_size - 1]]  # id 0 is <s>
    return words


def sort_key(word: str) -> tuple[bytes, bytes]:
    return (textnorm.fold(word).encode("utf-8"), word.encode("utf-8"))


OCR_PRONOUNS = {"l", "l'm", "l'll", "l'd", "l've"}


def ocr_repairs(uni: Counter, init: Counter, ratio: float = 10.0, min_target: int = 100) -> dict[str, str]:
    """Subtitle OCR often reads a capital I as a lower-case l ("lt's", "l'm", "lk", "lemand"). Map a token that
    starts with "l" to its "i" variant when the variant is >= ratio times more frequent and the token mostly occurs
    sentence-initially (where the capital I was) or is an English pronoun form."""
    out = {}
    for t, c in uni.items():
        if not t.startswith("l") or c < 2:
            continue
        target = "i" + t[1:]
        tc = uni.get(target, 0)
        if tc < min_target or tc < ratio * c:
            continue
        if t in OCR_PRONOUNS or init.get(t, 0) >= 0.5 * c:
            out[t] = target
    return out


def casing_flags(words: list[str], noninit: Counter, cased: Counter, min_obs: int = 3,
                 threshold: float = 0.6) -> dict[str, str]:
    """Return {word: dominant cased form} for words that are mostly written with capitals mid-sentence."""
    forms: dict[str, Counter] = {}
    wordset = set(words)
    for form, c in cased.items():
        lw = form.lower()
        if lw in wordset:
            forms.setdefault(lw, Counter())[form] += c
    out = {}
    for w, fc in forms.items():
        total = noninit.get(w, 0)
        if total < min_obs:
            continue
        capped = sum(fc.values())
        if capped / total >= threshold:
            # Ties go to the form with fewer capitals ("Bekkers" before "BEKKERS"), then the smallest, so the result
            # does not depend on the order in which the parallel workers' counters were merged.
            out[w] = min(fc.items(), key=lambda kv: (-kv[1], sum(c.isupper() for c in kv[0]), kv[0]))[0]
    return out


# --------------------------------------------------------------------------------------------------------------
# Pass 2


def _pass2(job):
    idx, (path, start, end) = job
    vocab = _VOCAB
    unk = _UNK  # ids 1..V-1 are words, V = <unk> (the dict also holds OCR aliases)
    ids: list[int] = []
    get = vocab.get
    for line in read_chunk(path, start, end):
        if not line:
            continue
        segs = line.split("\t")
        segs_lower = [s.lower() for s in segs]
        if _excluded(segs_lower):
            continue
        for sl in segs_lower:
            ids.append(0)
            ids.extend([get(t, unk) for t in sl.split(" ")])
    a = np.asarray(ids, dtype=np.int64)
    del ids
    n_uni = np.bincount(a[a != 0], minlength=unk + 1)
    bits, npart = _ID_BITS, _NPART
    mask = (1 << bits) - 1
    # bigrams (y, z): z != <s>
    y, z = a[:-1], a[1:]
    m = z != 0
    bi = (y[m] << bits) | z[m]
    # trigrams (x, y, z): y != <s> and z != <s>
    x, y2, z2 = a[:-2], a[1:-1], a[2:]
    m3 = (y2 != 0) & (z2 != 0)
    tri = (x[m3] << (2 * bits)) | (y2[m3] << bits) | z2[m3]
    del a, x, y, z, y2, z2, m, m3
    res = {}
    for name, keys, ctx_shift in (("bi", bi, bits), ("tri", tri, bits)):
        k, c = np.unique(keys, return_counts=True)
        part = ((k >> ctx_shift) & mask) % npart
        order = np.argsort(part, kind="stable")
        k, c, part = k[order], c[order].astype(np.uint32), part[order]
        bounds = np.searchsorted(part, np.arange(npart + 1))
        for p in range(npart):
            s, e = bounds[p], bounds[p + 1]
            np.save(os.path.join(_OUTDIR, f"{name}.p{p:02d}.c{idx:04d}.k.npy"), k[s:e])
            np.save(os.path.join(_OUTDIR, f"{name}.p{p:02d}.c{idx:04d}.c.npy"), c[s:e])
        res[name] = len(k)
    return idx, n_uni, int(np.sum(n_uni))


def merge_partition(outdir: str, name: str, p: int) -> tuple[np.ndarray, np.ndarray]:
    ks, cs = [], []
    for fn in sorted(os.listdir(outdir)):
        if fn.startswith(f"{name}.p{p:02d}.c") and fn.endswith(".k.npy"):
            ks.append(np.load(os.path.join(outdir, fn)))
            cs.append(np.load(os.path.join(outdir, fn[:-6] + ".c.npy")))
    k = np.concatenate(ks)
    c = np.concatenate(cs).astype(np.int64)
    uk, inv = np.unique(k, return_inverse=True)
    uc = np.bincount(inv, weights=c, minlength=len(uk)).astype(np.int64)
    return uk, uc


def run(prep_files: list[str], outdir: str, exclusion_files: list[str], vocab_size: int, min_count: int,
        min_df: int = 3, workers: int = 8, chunk_bytes: int = 24 << 20, block_lines: list[int] | None = None,
        npart: int = NPART) -> dict:
    """[block_lines]: lines per dispersion block, one value for all prepared files or one per file (default
    BLOCK_LINES). Id bits grow beyond ID_BITS when the vocabulary needs it; [npart] partitions bound the memory of
    the merge and of lmtool.kn (more partitions for bigger corpora)."""
    global _EXCL, _VOCAB, _OUTDIR, _UNK, _BLOCK, _ID_BITS, _NPART
    os.makedirs(outdir, exist_ok=True)
    t0 = time.time()
    _EXCL = load_exclusions(exclusion_files)
    if block_lines:
        if len(block_lines) not in (1, len(prep_files)):
            raise ValueError("block_lines needs one value or one per prepared file")
        sizes = block_lines * len(prep_files) if len(block_lines) == 1 else block_lines
        _BLOCK = dict(zip(prep_files, sizes))
    else:
        _BLOCK = {}
    _NPART = npart
    chunks = file_chunks(prep_files, chunk_bytes)
    ctx = mp.get_context("fork")
    uni: Counter = Counter()
    init: Counter = Counter()
    noninit: Counter = Counter()
    cased: Counter = Counter()
    df: Counter = Counter()
    dropped = lines = 0
    with ctx.Pool(workers) as pool:
        for u, i, n, c, f, d, nl in pool.imap_unordered(_pass1, chunks):
            uni.update(u)
            init.update(i)
            noninit.update(n)
            cased.update(c)
            df.update(f)
            dropped += d
            lines += nl
    print(f"  pass1: {lines:,} lines, {dropped} excluded, {len(uni):,} types, {sum(uni.values()):,} tokens, "
          f"{time.time() - t0:.0f}s", file=sys.stderr, flush=True)
    ocr = ocr_repairs(uni, init)
    for t, target in ocr.items():
        uni[target] += uni.pop(t)
        df[target] = max(df.get(target, 0), df.pop(t, 0))
    words = choose_vocab(uni, df, vocab_size, min_count, min_df)
    words.sort(key=sort_key)
    caps = casing_flags(words, noninit, cased)
    n_types = len(uni)
    min_count_effective = int(min(uni[w] for w in words))
    del uni, init, noninit, cased, df  # not needed any more; keeps the forked pass-2 workers small
    _VOCAB = {w: i + 1 for i, w in enumerate(words)}
    _UNK = len(words) + 1
    # <unk> (id V = len(words) + 1) must fit the id field of the n-gram keys.
    _ID_BITS = max(ID_BITS, _UNK.bit_length())
    if 3 * _ID_BITS > 63:
        raise ValueError(f"vocabulary too large for 64-bit trigram keys: {_UNK}")
    for t, target in ocr.items():
        if target in _VOCAB:
            _VOCAB[t] = _VOCAB[target]
    _OUTDIR = os.path.join(outdir, "chunks")
    os.makedirs(_OUTDIR, exist_ok=True)
    for fn in os.listdir(_OUTDIR):
        os.remove(os.path.join(_OUTDIR, fn))
    V = len(words) + 1
    uni_counts = np.zeros(V + 1, dtype=np.int64)
    ntok = 0
    with ctx.Pool(workers) as pool:
        for idx, n_uni, nt in pool.imap_unordered(_pass2, list(enumerate(chunks))):
            uni_counts += n_uni
            ntok += nt
    print(f"  pass2: {len(chunks)} chunks counted, {time.time() - t0:.0f}s", file=sys.stderr, flush=True)
    # merge partitions
    stats = {"bi": 0, "tri": 0}
    for p in range(npart):
        for name in ("bi", "tri"):
            k, c = merge_partition(_OUTDIR, name, p)
            np.save(os.path.join(outdir, f"{name}.p{p:02d}.k.npy"), k)
            np.save(os.path.join(outdir, f"{name}.p{p:02d}.c.npy"), c)
            stats[name] += len(k)
    for fn in os.listdir(_OUTDIR):
        os.remove(os.path.join(_OUTDIR, fn))
    os.rmdir(_OUTDIR)
    np.save(os.path.join(outdir, "uni.c.npy"), uni_counts)
    with open(os.path.join(outdir, "vocab.txt"), "w", encoding="utf-8") as f:
        f.write("<s>\n")
        for w in words:
            f.write(w + "\n")
    with open(os.path.join(outdir, "ocr_repairs.json"), "w", encoding="utf-8") as f:
        json.dump(ocr, f, ensure_ascii=False, indent=0, sort_keys=True)
    with open(os.path.join(outdir, "casing.json"), "w", encoding="utf-8") as f:
        json.dump(caps, f, ensure_ascii=False, indent=0, sort_keys=True)
    oov = int(uni_counts[V])
    prep_meta = []
    for p in prep_files:
        if os.path.exists(p + ".json"):
            with open(p + ".json") as f:
                m = json.load(f)
            m.pop("row_groups", None)
            prep_meta.append(m)
    meta = {
        "prep_files": [os.path.basename(p) for p in prep_files], "prep_meta": prep_meta, "lines": lines, "excluded_lines": dropped,
        "exclusion_sentences": len(_EXCL), "tokens": ntok, "types": n_types, "vocab_size": V,
        "min_count": min_count, "min_df": min_df, "min_count_effective": min_count_effective, "oov_tokens": oov,
        "oov_rate": oov / max(1, ntok), "unique_bigrams": stats["bi"], "unique_trigrams": stats["tri"],
        "capitalized_words": len(caps), "ocr_repairs": len(ocr), "seconds": round(time.time() - t0, 1),
    }
    if _BLOCK and any(n != BLOCK_LINES for n in _BLOCK.values()):
        meta["block_lines"] = [_BLOCK.get(p, BLOCK_LINES) for p in prep_files]
    # Key layout for lmtool.kn (only written when it differs from the defaults, so default counts stay unchanged).
    if _ID_BITS != ID_BITS:
        meta["id_bits"] = _ID_BITS
    if npart != NPART:
        meta["partitions"] = npart
    with open(os.path.join(outdir, "counts.json"), "w") as f:
        json.dump(meta, f, indent=1)
    print("  " + json.dumps(meta), file=sys.stderr, flush=True)
    return meta
