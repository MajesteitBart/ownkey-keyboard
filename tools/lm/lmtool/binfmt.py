"""Ownkey n-gram model binary format (versions 1 and 2): writer and Python reference reader.

See tools/lm/FORMAT.md for the byte layout. The reader here is the reference that the Kotlin reader
(org.florisboard.lib.prediction.ngram.NgramModel) must agree with.
"""

from __future__ import annotations

import heapq
import json
import math
import os
import struct

import numpy as np

from . import textnorm

MAGIC = b"OKNG"
VERSION = 2
VERSION_1 = 1
SUPPORTED_VERSIONS = (VERSION_1, VERSION)
HEADER_SIZE = 32
SECTION_ORDER_V1 = [b"META", b"VOCB", b"FOLD", b"CASE", b"UNIG", b"UTOP", b"QTBL", b"BIGR", b"TRIG"]
# Version 2 adds VALD (word validity bit set) after CASE; every other section is unchanged.
SECTION_ORDER = [b"META", b"VOCB", b"FOLD", b"CASE", b"VALD", b"UNIG", b"UTOP", b"QTBL", b"BIGR", b"TRIG"]
UNIGRAM_SCALE = 1024.0
UNIGRAM_BOS_Q = 0xFFFF
UTOP_SIZE = 1024
MAX_Q_BITS = 12
FLAG_TITLE, FLAG_UPPER, FLAG_OTHER = 1, 2, 4
NEG_INF = float("-inf")


# ------------------------------------------------------------------------------------------------ quantisation


def kmeans1d(values: np.ndarray, k: int, iters: int = 25) -> np.ndarray:
    v = np.sort(np.asarray(values, dtype=np.float64))
    if len(v) == 0:
        return np.zeros(k)
    uniq = np.unique(v)
    if len(uniq) <= k:
        c = np.concatenate([uniq, np.full(k - len(uniq), uniq[-1])])
        return np.sort(c)
    c = np.unique(np.quantile(v, (np.arange(k) + 0.5) / k))
    if len(c) < k:  # many repeated values: start from evenly spaced distinct values instead
        c = uniq[np.round(np.linspace(0, len(uniq) - 1, k)).astype(np.int64)]
    for _ in range(iters):
        mids = (c[1:] + c[:-1]) / 2
        a = np.searchsorted(mids, v)
        sums = np.bincount(a, weights=v, minlength=k)
        cnt = np.bincount(a, minlength=k)
        newc = np.where(cnt > 0, sums / np.maximum(cnt, 1), c)
        newc = np.sort(newc)
        if np.allclose(newc, c):
            break
        c = newc
    return c


def encode(values: np.ndarray, centers: np.ndarray) -> np.ndarray:
    """Nearest-centre codes; centres sorted ascending."""
    c = np.asarray(centers, dtype=np.float64)
    mids = (c[1:] + c[:-1]) / 2
    return np.searchsorted(mids, values).astype(np.int64)


# ------------------------------------------------------------------------------------------------ writer


def _varint(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def _strings_blob(strings: list[str]) -> tuple[bytes, bytes]:
    enc = [s.encode("utf-8") for s in strings]
    for e in enc:
        assert len(e) <= 255, e
    return bytes(len(e) for e in enc), b"".join(enc)


def _pack_entries(ids: np.ndarray, codes: np.ndarray, id_bits: int, entry_bytes: int) -> bytes:
    vals = (codes.astype(np.uint64) << np.uint64(id_bits)) | ids.astype(np.uint64)
    b = vals.astype("<u8").view(np.uint8).reshape(-1, 8)[:, :entry_bytes]
    return b.tobytes()


def write_model(path: str, count_dir: str, model, meta: dict, entry_bytes: int = 3, valid=None, listed=None) -> dict:
    """Writes version 2 when [valid] and [listed] (one bool per vocabulary id each, see lmtool/validity.py) are given,
    else version 1 (no VALD section)."""
    with open(os.path.join(count_dir, "vocab.txt"), encoding="utf-8") as f:
        words = [l.rstrip("\n") for l in f]
    with open(os.path.join(count_dir, "casing.json"), encoding="utf-8") as f:
        casing = json.load(f)
    return write_model_raw(path, words, casing, model, meta, entry_bytes, valid, listed)


def _bits(flags) -> bytes:
    v = np.asarray(flags, dtype=bool).copy()
    if len(v):
        v[0] = False
    return np.packbits(v, bitorder="little").tobytes()


def validity_section(valid, listed) -> bytes:
    """VALD payload: two bit sets of ceil(V / 8) bytes, validated words then listed (dictionary) words. Bit (i & 7) of
    byte (i >> 3) stands for word id i; <s> (id 0) is never set. Every listed word is also validated."""
    valid = np.asarray(valid, dtype=bool) | np.asarray(listed, dtype=bool)
    return _bits(valid) + _bits(listed)


def write_model_raw(path: str, words: list[str], casing: dict, model, meta: dict, entry_bytes: int = 3,
                    valid=None, listed=None) -> dict:
    V = len(words)
    assert (valid is None) == (listed is None)
    assert valid is None or (len(valid) == V and len(listed) == V)
    assert words[0] == textnorm.BOS and V == model.V
    keys = [(textnorm.fold(w).encode("utf-8"), w.encode("utf-8")) for w in words[1:]]
    assert all(keys[i] < keys[i + 1] for i in range(len(keys) - 1)), "vocabulary not sorted by (fold, word)"
    # Probability codes take at most MAX_Q_BITS bits; the id field gets the remaining bits of the entry.
    q_bits = min(8 * entry_bytes - max(1, (V - 1).bit_length()), MAX_Q_BITS)
    id_bits = 8 * entry_bytes - q_bits
    assert q_bits >= 4, "entry too small for vocabulary"
    sections: dict[bytes, bytes] = {}

    sections[b"META"] = json.dumps(meta, ensure_ascii=False, sort_keys=True, default=float).encode("utf-8")
    lens, blob = _strings_blob(words)
    sections[b"VOCB"] = lens + blob

    fold_ids, fold_strs = [], []
    for i, w in enumerate(words):
        if i == 0:
            continue
        fw = textnorm.fold(w)
        if fw != w:
            fold_ids.append(i)
            fold_strs.append(fw)
    fl, fb = _strings_blob(fold_strs)
    sections[b"FOLD"] = struct.pack("<I", len(fold_ids)) + np.asarray(fold_ids, "<u4").tobytes() + fl + fb

    flags = np.zeros(V, dtype=np.uint8)
    other_ids, other_forms = [], []
    for i, w in enumerate(words):
        form = casing.get(w)
        if not form or form == w or form.lower() != w:
            continue
        if form == w[0].upper() + w[1:]:
            flags[i] = FLAG_TITLE
        elif form == w.upper():
            flags[i] = FLAG_UPPER
        else:
            flags[i] = FLAG_OTHER
            other_ids.append(i)
            other_forms.append(form)
    ol, ob = _strings_blob(other_forms)
    sections[b"CASE"] = flags.tobytes() + struct.pack("<I", len(other_ids)) + np.asarray(other_ids, "<u4").tobytes() + ol + ob
    if valid is not None:
        sections[b"VALD"] = validity_section(valid, listed)

    lp1 = model.logp1.copy()
    q1 = np.clip(np.round(-lp1[1:] * UNIGRAM_SCALE), 0, UNIGRAM_BOS_Q - 1).astype(np.int64)
    uq = np.concatenate([[UNIGRAM_BOS_Q], q1]).astype("<u2")
    sections[b"UNIG"] = uq.tobytes()
    order = np.lexsort((np.arange(1, V), uq[1:]))[:UTOP_SIZE] + 1  # ascending q = descending prob, ties by id
    sections[b"UTOP"] = struct.pack("<I", len(order)) + order.astype("<u4").tobytes()

    nq = 1 << q_bits
    cb_bi = kmeans1d(model.bi_lp, nq).astype(np.float32)
    cb_tri = kmeans1d(model.tri_lp, nq).astype(np.float32)
    nz2 = model.bow2[model.bow2 != 0]
    cb_b2 = np.concatenate([[0.0], kmeans1d(nz2, 255)]).astype(np.float32)
    cb_b3 = kmeans1d(model.tctx_bow, 256).astype(np.float32)
    # keep code 0 of the bigram backoff table exactly 0.0 (contexts without a list)
    sections[b"QTBL"] = cb_bi.astype("<f4").tobytes() + cb_tri.astype("<f4").tobytes() + \
        cb_b2.astype("<f4").tobytes() + cb_b3.astype("<f4").tobytes()

    # bigrams
    blen = np.bincount(model.bi_v, minlength=V)
    bcode = np.zeros(V, dtype=np.int64)
    has = model.bow2 != 0
    if np.any(has):
        bcode[has] = encode(model.bow2[has], cb_b2[1:].astype(np.float64)) + 1
    bi_codes = encode(model.bi_lp, cb_bi.astype(np.float64))
    sections[b"BIGR"] = b"".join(_varint(int(c)) for c in blen) + bcode.astype(np.uint8).tobytes() + \
        _pack_entries(model.bi_w, bi_codes, id_bits, entry_bytes)

    # trigrams
    tcount = np.bincount(model.tctx_v, minlength=V)
    tlen_per_ctx = np.zeros(len(model.tctx_u), dtype=np.int64)
    if len(model.tri_u):
        ckey = model.tri_v * (1 << 24) + model.tri_u
        ctxkey = model.tctx_v * (1 << 24) + model.tctx_u
        pos = np.searchsorted(ctxkey, ckey)
        assert np.all(ctxkey[pos] == ckey)
        tlen_per_ctx = np.bincount(pos, minlength=len(ctxkey))
    assert tlen_per_ctx.max(initial=0) <= 255
    tb_codes = encode(model.tctx_bow, cb_b3.astype(np.float64))
    ctx_rec = np.zeros((len(model.tctx_u), 5), dtype=np.uint8)
    u = model.tctx_u.astype(np.int64)
    ctx_rec[:, 0] = u & 0xFF
    ctx_rec[:, 1] = (u >> 8) & 0xFF
    ctx_rec[:, 2] = (u >> 16) & 0xFF
    ctx_rec[:, 3] = tlen_per_ctx
    ctx_rec[:, 4] = tb_codes
    tri_codes = encode(model.tri_lp, cb_tri.astype(np.float64))
    sections[b"TRIG"] = b"".join(_varint(int(c)) for c in tcount) + ctx_rec.tobytes() + \
        _pack_entries(model.tri_w, tri_codes, id_bits, entry_bytes)

    # assemble
    version = VERSION if valid is not None else VERSION_1
    order_tags = SECTION_ORDER if valid is not None else SECTION_ORDER_V1
    nsec = len(order_tags)
    table_size = nsec * 12
    offset = HEADER_SIZE + table_size
    body = bytearray()
    table = bytearray()
    for tag in order_tags:
        data = sections[tag]
        pad = (-offset) % 4
        body += b"\0" * pad
        offset += pad
        table += tag + struct.pack("<II", offset, len(data))
        body += data
        offset += len(data)
    total = HEADER_SIZE + table_size + len(body)
    header = MAGIC + struct.pack("<HHIBBHIIII", version, nsec, V, id_bits, entry_bytes, 0,
                                 len(model.bi_w), len(model.tctx_u), len(model.tri_w), total)
    assert len(header) == HEADER_SIZE
    with open(path, "wb") as f:
        f.write(header)
        f.write(table)
        f.write(body)
    return {
        "path": path, "bytes": total, "vocab": V, "id_bits": id_bits, "q_bits": q_bits,
        "bigrams": int(len(model.bi_w)), "trigram_contexts": int(len(model.tctx_u)),
        "trigrams": int(len(model.tri_w)), "capitalized": int((flags != 0).sum()), "version": version,
        "valid": None if valid is None else int(np.asarray(valid, dtype=bool)[1:].sum()),
        "listed": None if listed is None else int(np.asarray(listed, dtype=bool)[1:].sum()),
        "section_bytes": {t.decode(): len(d) for t, d in sections.items()},
    }


# ------------------------------------------------------------------------------------------------ reader


class NgramModel:
    """Reference reader (pure Python + numpy)."""

    BOS_ID = 0

    def __init__(self, data: bytes):
        if data[:4] != MAGIC:
            raise ValueError("bad magic")
        (self.version, nsec, V, self.id_bits, self.entry_bytes, _r, nbi, ntc, ntri, total) = \
            struct.unpack_from("<HHIBBHIIII", data, 4)
        if self.version not in SUPPORTED_VERSIONS:
            raise ValueError(f"unsupported version {self.version}")
        if total != len(data):
            raise ValueError("truncated file")
        self.V = V
        sec = {}
        for i in range(nsec):
            tag = data[HEADER_SIZE + 12 * i: HEADER_SIZE + 12 * i + 4]
            off, ln = struct.unpack_from("<II", data, HEADER_SIZE + 12 * i + 4)
            sec[tag] = memoryview(data)[off:off + ln]
        self.meta = json.loads(bytes(sec[b"META"]).decode("utf-8"))
        vb = sec[b"VOCB"]
        lens = np.frombuffer(vb[:V], dtype=np.uint8).astype(np.int64)
        self.voff = np.concatenate([[0], np.cumsum(lens)]).astype(np.int64)
        self.vblob = bytes(vb[V:])
        self.words = [self.vblob[self.voff[i]:self.voff[i + 1]].decode("utf-8") for i in range(V)]
        fb = sec[b"FOLD"]
        (nf,) = struct.unpack_from("<I", fb, 0)
        fids = np.frombuffer(fb[4:4 + 4 * nf], dtype="<u4")
        flens = np.frombuffer(fb[4 + 4 * nf:4 + 5 * nf], dtype=np.uint8).astype(np.int64)
        foff = np.concatenate([[0], np.cumsum(flens)]) + 4 + 5 * nf
        self.folded = list(self.words)
        for j, i in enumerate(fids):
            self.folded[int(i)] = bytes(fb[foff[j]:foff[j + 1]]).decode("utf-8")
        self.folded_bytes = [f.encode("utf-8") for f in self.folded]
        self.folded_bytes[0] = self.words[0].encode("utf-8")
        cb = sec[b"CASE"]
        self.flags = np.frombuffer(cb[:V], dtype=np.uint8)
        (no,) = struct.unpack_from("<I", cb, V)
        oids = np.frombuffer(cb[V + 4:V + 4 + 4 * no], dtype="<u4")
        olens = np.frombuffer(cb[V + 4 + 4 * no:V + 4 + 5 * no], dtype=np.uint8).astype(np.int64)
        ooff = np.concatenate([[0], np.cumsum(olens)]) + V + 4 + 5 * no
        self.other_forms = {int(i): bytes(cb[ooff[j]:ooff[j + 1]]).decode("utf-8") for j, i in enumerate(oids)}
        # Version 1 has no validity section: every word counts as validated, none as listed (no word list is known,
        # so the context-aware real-word margin must not treat names and slang as dictionary words).
        self.valid = np.ones(V, dtype=bool)
        self.listed = np.zeros(V, dtype=bool)
        if self.version >= 2:
            if b"VALD" not in sec:
                raise ValueError("missing section VALD")
            vb = np.frombuffer(sec[b"VALD"], dtype=np.uint8)
            nb = (V + 7) // 8
            if len(vb) != 2 * nb:
                raise ValueError("bad VALD size")
            self.valid = np.unpackbits(vb[:nb], bitorder="little")[:V].astype(bool)
            self.listed = np.unpackbits(vb[nb:], bitorder="little")[:V].astype(bool)
        self.valid[0] = False
        self.listed[0] = False
        uq = np.frombuffer(sec[b"UNIG"], dtype="<u2").astype(np.int64)
        self.uni = np.where(uq == UNIGRAM_BOS_Q, -np.inf, -uq / UNIGRAM_SCALE).astype(np.float32)
        ub = sec[b"UTOP"]
        (nt,) = struct.unpack_from("<I", ub, 0)
        self.utop = np.frombuffer(ub[4:4 + 4 * nt], dtype="<u4").astype(np.int64)
        q_bits = 8 * self.entry_bytes - self.id_bits
        nq = 1 << q_bits
        qt = np.frombuffer(sec[b"QTBL"], dtype="<f4")
        self.cb_bi, self.cb_tri = qt[:nq], qt[nq:2 * nq]
        self.cb_b2, self.cb_b3 = qt[2 * nq:2 * nq + 256], qt[2 * nq + 256:2 * nq + 512]
        idmask = (1 << self.id_bits) - 1

        def unpack(buf, n):
            a = np.frombuffer(buf[:n * self.entry_bytes], dtype=np.uint8).reshape(n, self.entry_bytes).astype(np.int64)
            val = np.zeros(n, dtype=np.int64)
            for b in range(self.entry_bytes):
                val |= a[:, b] << (8 * b)
            return val & idmask, val >> self.id_bits

        def varints(raw: bytes, count: int) -> tuple[np.ndarray, int]:
            out = np.zeros(count, dtype=np.int64)
            pos = 0
            for i in range(count):
                n = shift = 0
                while True:
                    b = raw[pos]
                    pos += 1
                    n |= (b & 0x7F) << shift
                    shift += 7
                    if b < 0x80:
                        break
                out[i] = n
            return out, pos

        bb = bytes(sec[b"BIGR"])
        blen, pos = varints(bb, V)
        self.bstart = np.concatenate([[0], np.cumsum(blen)]).astype(np.int64)
        self.bow2 = self.cb_b2[np.frombuffer(bb[pos:pos + V], dtype=np.uint8)]
        ids, codes = unpack(bb[pos + V:], nbi)
        self.bi_ids, self.bi_lp = ids, self.cb_bi[codes]
        raw = bytes(sec[b"TRIG"])
        counts, pos = varints(raw, V)
        self.tcstart = np.concatenate([[0], np.cumsum(counts)]).astype(np.int64)
        rec = np.frombuffer(raw[pos:pos + 5 * ntc], dtype=np.uint8).reshape(ntc, 5).astype(np.int64)
        self.tc_u = rec[:, 0] | (rec[:, 1] << 8) | (rec[:, 2] << 16)
        self.tc_start = np.concatenate([[0], np.cumsum(rec[:, 3])]).astype(np.int64)
        self.tc_bow = self.cb_b3[rec[:, 4]]
        ids, codes = unpack(raw[pos + 5 * ntc:], ntri)
        self.tri_ids, self.tri_lp = ids, self.cb_tri[codes]
        self._wordindex = {w: i for i, w in enumerate(self.words)}

    @classmethod
    def load(cls, path: str) -> "NgramModel":
        with open(path, "rb") as f:
            return cls(f.read())

    # ---- vocabulary
    @property
    def vocabulary_size(self) -> int:
        return self.V

    def word_id(self, word: str) -> int:
        return self._wordindex.get(word, -1)

    def word(self, i: int) -> str:
        return self.words[i]

    def is_capitalized(self, i: int) -> bool:
        return bool(self.flags[i])

    def is_valid(self, i: int) -> bool:
        """Validated word (curated list, capitalised or frequent; see lmtool/validity.py); always True in v1."""
        return 0 < i < self.V and bool(self.valid[i])

    def is_listed(self, i: int) -> bool:
        """Word of the curated word list (a dictionary word); always False in v1."""
        return 0 < i < self.V and bool(self.listed[i])

    def cased_form(self, i: int) -> str:
        w = self.words[i]
        f = int(self.flags[i])
        if f == FLAG_TITLE:
            return w[0].upper() + w[1:]
        if f == FLAG_UPPER:
            return w.upper()
        if f == FLAG_OTHER:
            return self.other_forms[i]
        return w

    def prefix_range(self, prefix: str) -> tuple[int, int]:
        p = textnorm.fold(textnorm.normalize_word(prefix)).encode("utf-8")
        fb = self.folded_bytes
        lo, hi = 1, self.V
        while lo < hi:
            mid = (lo + hi) // 2
            if fb[mid] < p:
                lo = mid + 1
            else:
                hi = mid
        start = lo
        hi = self.V
        while lo < hi:
            mid = (lo + hi) // 2
            if fb[mid][:len(p)] <= p:
                lo = mid + 1
            else:
                hi = mid
        return start, lo

    # ---- probabilities
    def unigram_logprob(self, i: int) -> float:
        return float(self.uni[i])

    def _tctx(self, p1: int, p2: int) -> int:
        if p1 <= 0 or p2 < 0 or p1 >= self.V or p2 >= self.V:
            return -1
        s, e = self.tcstart[p1], self.tcstart[p1 + 1]
        j = s + int(np.searchsorted(self.tc_u[s:e], p2))
        if j < e and self.tc_u[j] == p2:
            return int(j)
        return -1

    def logprob(self, w: int, p1: int = -1, p2: int = -1) -> float:
        if w <= 0 or w >= self.V:
            return NEG_INF
        acc = np.float32(0.0)
        c = self._tctx(p1, p2)
        if c >= 0:
            s, e = self.tc_start[c], self.tc_start[c + 1]
            hit = np.flatnonzero(self.tri_ids[s:e] == w)
            if len(hit):
                return float(self.tri_lp[s + hit[0]])
            acc = np.float32(acc + self.tc_bow[c])
        if 0 <= p1 < self.V:
            s, e = self.bstart[p1], self.bstart[p1 + 1]
            hit = np.flatnonzero(self.bi_ids[s:e] == w)
            if len(hit):
                return float(np.float32(acc + self.bi_lp[s + hit[0]]))
            acc = np.float32(acc + self.bow2[p1])
        return float(np.float32(acc + self.uni[w]))

    def _sources(self, p1: int, p2: int):
        """(ids, logprobs) arrays of the trigram list, bigram list and backoff offsets."""
        c = self._tctx(p1, p2)
        tri_ids = tri_lp = np.zeros(0, dtype=np.int64)
        bow3 = np.float32(0.0)
        if c >= 0:
            s, e = self.tc_start[c], self.tc_start[c + 1]
            tri_ids, tri_lp = self.tri_ids[s:e], self.tri_lp[s:e]
            bow3 = self.tc_bow[c]
        bi_ids = bi_lp = np.zeros(0, dtype=np.int64)
        bow2 = np.float32(0.0)
        if 0 <= p1 < self.V:
            s, e = self.bstart[p1], self.bstart[p1 + 1]
            bi_ids, bi_lp = self.bi_ids[s:e], self.bi_lp[s:e]
            bow2 = self.bow2[p1]
        return tri_ids, tri_lp, bow3, bi_ids, bi_lp, bow2

    def top_next(self, p1: int, p2: int = -1, limit: int = 3) -> list[tuple[int, float]]:
        tri_ids, tri_lp, bow3, bi_ids, bi_lp, bow2 = self._sources(p1, p2)
        cand: dict[int, float] = {}
        for i, lp in zip(tri_ids.tolist(), tri_lp.tolist()):
            if i not in cand:
                cand[i] = lp
        n = 0
        for i, lp in zip(bi_ids.tolist(), bi_lp.tolist()):
            if i not in cand:
                cand[i] = float(np.float32(bow3 + np.float32(lp)))
                n += 1
                if n >= limit:
                    break
        off = np.float32(bow3 + bow2)
        n = 0
        for i in self.utop.tolist():
            if i not in cand:
                cand[i] = float(np.float32(off + self.uni[i]))
                n += 1
                if n >= limit:
                    break
        return sorted(cand.items(), key=lambda x: (-x[1], x[0]))[:limit]

    def top_completions(self, prefix: str, p1: int, p2: int = -1, limit: int = 3) -> list[tuple[int, float]]:
        lo, hi = self.prefix_range(prefix)
        if lo == 1 and hi == self.V:
            return self.top_next(p1, p2, limit)
        if lo >= hi:
            return []
        tri_ids, tri_lp, bow3, bi_ids, bi_lp, bow2 = self._sources(p1, p2)
        cand: dict[int, float] = {}
        for i, lp in zip(tri_ids.tolist(), tri_lp.tolist()):
            if lo <= i < hi and i not in cand:
                cand[i] = lp
        for i, lp in zip(bi_ids.tolist(), bi_lp.tolist()):
            if lo <= i < hi and i not in cand:
                cand[i] = float(np.float32(bow3 + np.float32(lp)))
        off = np.float32(bow3 + bow2)
        seg = self.uni[lo:hi]
        k = min(len(seg), limit + len(cand))
        if k > 0:
            idx = np.argsort(-seg, kind="stable")[:k]  # ties: lower id first
            for j in idx.tolist():
                i = lo + j
                if i not in cand:
                    cand[i] = float(np.float32(off + seg[j]))
        return sorted(cand.items(), key=lambda x: (-x[1], x[0]))[:limit]
