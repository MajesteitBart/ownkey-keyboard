"""Stage 3: interpolated modified Kneser-Ney estimation + entropy-style pruning into a backoff model.

The full (unpruned) model is interpolated modified KN (Chen & Goodman 1998) with three discounts per order:

  P3(w|u,v) = max(c(uvw) - D3(c), 0) / c(uv.) + g3(uv) * P2(w|v)
  P2(w|v)   = max(a(vw)  - D2(a), 0) / a(v.)  + g2(v)  * P1(w)        a = continuation count N1+(. v w)
  P1(w)     = max(a(w)   - D1(a), 0) / a(.)   + g1     / |V|          a = continuation count N1+(. w)

Bigrams that start with <s> use raw counts (they cannot be extended to the left), as in KenLM.

Pruning keeps, per context, the successors with the largest weighted log-ratio
  c(h,w) * (log P(w|h) - log(g(h) * P_backoff(w|h')))
(Seymore & Rosenfeld 1996) above a threshold and up to a cap, then stores the *interpolated* probability of each
kept entry and renormalises the backoff weight of every context (ARPA style):

  bow(h) = (1 - sum_{kept w} P(w|h)) / (1 - sum_{kept w} P_backoff(w|h'))

so the pruned model is a properly normalised backoff model: P(w|h) = stored if kept else bow(h) * P(w|h').
"""

from __future__ import annotations

import json
import os
import sys
import time
from dataclasses import dataclass, field

import numpy as np

from .count import ID_BITS, layout


@dataclass
class PruneConfig:
    bigram_threshold: float = 2.0
    trigram_threshold: float = 4.0
    bigram_cap: int = 64
    bos_bigram_cap: int = 255
    trigram_cap: int = 24
    trigram_min_count: int = 2
    bigram_min_count: int = 1
    trigram_context_min: int = 1


@dataclass
class Model:
    V: int  # output ids 0..V-1 (0 = <s>); id V = <unk> during estimation
    logp1: np.ndarray  # float64[V]  (index 0 = <s> = -inf)
    # bigrams: sorted by (v, -p)
    bi_v: np.ndarray
    bi_w: np.ndarray
    bi_lp: np.ndarray
    bow2: np.ndarray  # float64[V] log backoff per context word (0 if no list)
    # trigrams: sorted by (v, u, -p)
    tri_u: np.ndarray
    tri_v: np.ndarray
    tri_w: np.ndarray
    tri_lp: np.ndarray
    tctx_u: np.ndarray  # sorted by (v, u)
    tctx_v: np.ndarray
    tctx_bow: np.ndarray
    stats: dict = field(default_factory=dict)


def discounts(n: np.ndarray) -> tuple[float, float, float]:
    """n[1..4] count-of-counts -> (D1, D2, D3+) (Chen & Goodman), with fallbacks for degenerate tiny data."""
    n1, n2, n3, n4 = (float(n[i]) for i in range(1, 5))
    y = n1 / (n1 + 2 * n2) if n1 + 2 * n2 > 0 else 0.5
    d1 = 1 - 2 * y * n2 / n1 if n1 > 0 else 0.5
    d2 = 2 - 3 * y * n3 / n2 if n2 > 0 else 1.0
    d3 = 3 - 4 * y * n4 / n3 if n3 > 0 else 1.5
    return (min(max(d1, 0.05), 0.95), min(max(d2, 0.05), 1.95), min(max(d3, 0.05), 2.95))


def coc(c: np.ndarray) -> np.ndarray:
    """Count-of-counts n[0..4] with n[k] = number of entries whose count is exactly k (k <= 4)."""
    b = np.bincount(np.minimum(c, 5).astype(np.int64), minlength=6)
    return b[:5].astype(np.int64)


def disc_of(c: np.ndarray, d: tuple[float, float, float]) -> np.ndarray:
    return np.where(c >= 3, d[2], np.where(c == 2, d[1], np.where(c == 1, d[0], 0.0)))


def groups(ctx: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Sorted context keys -> (group start indices, group id per element)."""
    if len(ctx) == 0:
        return np.zeros(0, dtype=np.int64), np.zeros(0, dtype=np.int64)
    flag = np.empty(len(ctx), dtype=bool)
    flag[0] = True
    flag[1:] = ctx[1:] != ctx[:-1]
    starts = np.flatnonzero(flag)
    gid = np.cumsum(flag) - 1
    return starts, gid


def gamma_and_total(c: np.ndarray, starts: np.ndarray, gid: np.ndarray, d) -> tuple[np.ndarray, np.ndarray]:
    total = np.add.reduceat(c.astype(np.float64), starts)
    n1 = np.add.reduceat((c == 1).astype(np.float64), starts)
    n2 = np.add.reduceat((c == 2).astype(np.float64), starts)
    n3 = np.add.reduceat((c >= 3).astype(np.float64), starts)
    g = (d[0] * n1 + d[1] * n2 + d[2] * n3) / total
    return g, total


def select(score: np.ndarray, p: np.ndarray, gid: np.ndarray, starts: np.ndarray, cap_per_group: np.ndarray,
           keep_mask: np.ndarray, threshold: float) -> np.ndarray:
    """Indices kept: score >= threshold & keep_mask, top-cap by p within each group. Returned sorted by (gid, -p)."""
    order = np.lexsort((-p, gid))
    gs = gid[order]
    ok = keep_mask[order] & (score[order] >= threshold)
    # rank among eligible entries within the group
    okc = np.cumsum(ok)
    first = starts[gs]
    # number of eligible entries before this position within the group
    before_group = np.where(first > 0, okc[np.maximum(first - 1, 0)], 0)
    before_group = np.where(first > 0, before_group, 0)
    rank = okc - before_group - 1
    keep = ok & (rank < cap_per_group[gs])
    return order[keep]


def estimate(count_dir: str, cfg: PruneConfig, log=print) -> Model:
    t0 = time.time()
    with open(os.path.join(count_dir, "vocab.txt"), encoding="utf-8") as f:
        V = sum(1 for _ in f)
    UNK = V
    ID_BITS, NPART = layout(count_dir)  # key layout of this count directory (counts.json)
    ID_MASK = (1 << ID_BITS) - 1
    LOW = (1 << (2 * ID_BITS)) - 1
    # ---------------- pass A: global statistics
    tri_coc = np.zeros(5, dtype=np.int64)
    bi_coc = np.zeros(5, dtype=np.int64)
    a1 = np.zeros(V + 1, dtype=np.int64)
    n_tri = n_bi = 0
    for p in range(NPART):
        k3 = np.load(os.path.join(count_dir, f"tri.p{p:02d}.k.npy"))
        c3 = np.load(os.path.join(count_dir, f"tri.p{p:02d}.c.npy"))
        k2 = np.load(os.path.join(count_dir, f"bi.p{p:02d}.k.npy"))
        c2 = np.load(os.path.join(count_dir, f"bi.p{p:02d}.c.npy"))
        n_tri += len(k3)
        n_bi += len(k2)
        tri_coc += coc(c3)
        a2 = adjusted_bigram_counts(k3, k2, c2, ID_BITS)
        bi_coc += coc(a2)
        a1 += np.bincount((k2 & ID_MASK).astype(np.int64), minlength=V + 1)
        del k3, c3
    a1[0] = 0
    uni_coc = coc(a1[1:])
    D3 = discounts(tri_coc)
    D2 = discounts(bi_coc)
    D1 = discounts(uni_coc)
    log(f"  discounts D1={tuple(round(x, 3) for x in D1)} D2={tuple(round(x, 3) for x in D2)} "
        f"D3={tuple(round(x, 3) for x in D3)}  ({time.time() - t0:.0f}s)")
    # unigram (over ids 1..V, V = <unk>)
    A1 = float(a1[1:].sum())
    nw = V  # ids 1..V
    g1 = float(D1[0] * (a1[1:] == 1).sum() + D1[1] * (a1[1:] == 2).sum() + D1[2] * (a1[1:] >= 3).sum()) / A1
    p1 = np.zeros(V + 1)
    p1[1:] = np.maximum(a1[1:] - disc_of(a1[1:], D1), 0) / A1 + g1 / nw
    logp1_all = np.full(V + 1, -np.inf)
    logp1_all[1:] = np.log(p1[1:])

    # ---------------- pass B: per partition probabilities + pruning
    out_bi_v, out_bi_w, out_bi_lp = [], [], []
    bow2 = np.zeros(V + 1)
    out_t = {"u": [], "v": [], "w": [], "lp": []}
    out_tc = {"u": [], "v": [], "bow": []}
    for p in range(NPART):
        k3 = np.load(os.path.join(count_dir, f"tri.p{p:02d}.k.npy"))
        c3 = np.load(os.path.join(count_dir, f"tri.p{p:02d}.c.npy"))
        k2 = np.load(os.path.join(count_dir, f"bi.p{p:02d}.k.npy"))
        c2 = np.load(os.path.join(count_dir, f"bi.p{p:02d}.c.npy"))
        a2 = adjusted_bigram_counts(k3, k2, c2, ID_BITS)
        # ---- bigram probabilities
        v2 = (k2 >> ID_BITS).astype(np.int64)
        w2 = (k2 & ID_MASK).astype(np.int64)
        st2, gid2 = groups(v2)
        g2, tot2 = gamma_and_total(a2, st2, gid2, D2)
        pb = np.maximum(a2 - disc_of(a2, D2), 0) / tot2[gid2] + g2[gid2] * p1[w2]
        lpb = np.log(pb)
        ctxv = v2[st2]
        score2 = c2 * (lpb - np.log(g2[gid2]) - logp1_all[w2])
        caps2 = np.where(ctxv == 0, cfg.bos_bigram_cap, cfg.bigram_cap)
        mask2 = (w2 != UNK) & (v2 != UNK) & (c2 >= cfg.bigram_min_count)
        kept2 = select(score2, pb, gid2, st2, caps2, mask2, cfg.bigram_threshold)
        kv, kw, kp = v2[kept2], w2[kept2], pb[kept2]
        # backoff weights for contexts with kept entries
        if len(kept2):
            stk, gidk = groups(kv)
            sp = np.add.reduceat(kp, stk)
            sl = np.add.reduceat(p1[kw], stk)
            bow2[kv[stk]] = np.log(np.maximum(1 - sp, 1e-12)) - np.log(np.maximum(1 - sl, 1e-12))
        out_bi_v.append(kv)
        out_bi_w.append(kw)
        out_bi_lp.append(np.log(kp))
        # ---- trigram probabilities
        u3 = (k3 >> (2 * ID_BITS)).astype(np.int64)
        v3 = ((k3 >> ID_BITS) & ID_MASK).astype(np.int64)
        w3 = (k3 & ID_MASK).astype(np.int64)
        ctx3 = k3 >> ID_BITS
        st3, gid3 = groups(ctx3)
        g3, tot3 = gamma_and_total(c3, st3, gid3, D3)
        # unpruned bigram prob P2(w|v) for each trigram suffix
        suf = k3 & LOW
        pos = np.searchsorted(k2, suf)
        assert np.all(k2[np.minimum(pos, len(k2) - 1)] == suf), "trigram suffix missing from bigrams"
        p2full = pb[pos]
        pt = np.maximum(c3 - disc_of(c3, D3), 0) / tot3[gid3] + g3[gid3] * p2full
        # pruned backoff estimate P_bo(w|v)
        kept_keys = (kv << ID_BITS) | kw  # sorted by v, then -p: need sorted keys for searchsorted
        ko = np.argsort(kept_keys)
        kk_sorted = kept_keys[ko]
        kp_sorted = kp[ko]
        if len(kk_sorted):
            pos2 = np.searchsorted(kk_sorted, suf)
            pos2c = np.minimum(pos2, len(kk_sorted) - 1)
            hit = kk_sorted[pos2c] == suf
            pbo = np.where(hit, kp_sorted[pos2c], np.exp(bow2[v3]) * p1[w3])
        else:
            pbo = np.exp(bow2[v3]) * p1[w3]
        score3 = c3 * (np.log(pt) - np.log(g3[gid3]) - np.log(pbo))
        caps3 = np.full(len(st3), cfg.trigram_cap)
        ctx_total_ok = tot3 >= cfg.trigram_context_min
        mask3 = (w3 != UNK) & (v3 != UNK) & (u3 != UNK) & (c3 >= cfg.trigram_min_count) & ctx_total_ok[gid3]
        kept3 = select(score3, pt, gid3, st3, caps3, mask3, cfg.trigram_threshold)
        tu, tv, tw, tp, tbo = u3[kept3], v3[kept3], w3[kept3], pt[kept3], pbo[kept3]
        if len(kept3):
            stk, _ = groups(ctx3[kept3])
            sp = np.add.reduceat(tp, stk)
            sl = np.add.reduceat(tbo, stk)
            bow = np.log(np.maximum(1 - sp, 1e-12)) - np.log(np.maximum(1 - sl, 1e-12))
            out_tc["u"].append(tu[stk])
            out_tc["v"].append(tv[stk])
            out_tc["bow"].append(bow)
        out_t["u"].append(tu)
        out_t["v"].append(tv)
        out_t["w"].append(tw)
        out_t["lp"].append(np.log(tp))
        del k3, c3, k2, c2, a2, pt, pbo, score3
        log(f"  partition {p:2d}: bigrams kept {len(kept2):,}, trigrams kept {len(kept3):,} ({time.time() - t0:.0f}s)")

    bi_v = np.concatenate(out_bi_v)
    bi_w = np.concatenate(out_bi_w)
    bi_lp = np.concatenate(out_bi_lp)
    o = np.lexsort((-bi_lp, bi_v))
    bi_v, bi_w, bi_lp = bi_v[o], bi_w[o], bi_lp[o]
    tu = np.concatenate(out_t["u"])
    tv = np.concatenate(out_t["v"])
    tw = np.concatenate(out_t["w"])
    tlp = np.concatenate(out_t["lp"])
    o = np.lexsort((-tlp, tu, tv))
    tu, tv, tw, tlp = tu[o], tv[o], tw[o], tlp[o]
    cu = np.concatenate(out_tc["u"]) if out_tc["u"] else np.zeros(0, dtype=np.int64)
    cv = np.concatenate(out_tc["v"]) if out_tc["v"] else np.zeros(0, dtype=np.int64)
    cb = np.concatenate(out_tc["bow"]) if out_tc["bow"] else np.zeros(0)
    o = np.lexsort((cu, cv))
    cu, cv, cb = cu[o], cv[o], cb[o]
    stats = {
        "unique_trigrams": int(n_tri), "unique_bigrams": int(n_bi), "D1": D1, "D2": D2, "D3": D3,
        "bigrams_kept": int(len(bi_v)), "bigram_contexts": int(len(np.unique(bi_v))),
        "trigrams_kept": int(len(tu)), "trigram_contexts": int(len(cu)), "seconds": round(time.time() - t0, 1),
    }
    log("  " + json.dumps(stats))
    return Model(V=V, logp1=logp1_all[:V].copy(), bi_v=bi_v, bi_w=bi_w, bi_lp=bi_lp, bow2=bow2[:V].copy(),
                 tri_u=tu, tri_v=tv, tri_w=tw, tri_lp=tlp, tctx_u=cu, tctx_v=cv, tctx_bow=cb, stats=stats)


def adjusted_bigram_counts(k3: np.ndarray, k2: np.ndarray, c2: np.ndarray, id_bits: int = ID_BITS) -> np.ndarray:
    """Continuation counts N1+(. v w) for bigrams (v, w); raw counts for v == <s>."""
    suf = k3 & ((1 << (2 * id_bits)) - 1)
    us, cc = np.unique(suf, return_counts=True)
    pos = np.searchsorted(us, k2)
    posc = np.minimum(pos, max(len(us) - 1, 0))
    hit = (us[posc] == k2) if len(us) else np.zeros(len(k2), dtype=bool)
    a2 = np.where(hit, cc[posc] if len(us) else 0, 0).astype(np.int64)
    bos = (k2 >> id_bits) == 0
    a2[bos] = c2[bos]
    missing = (~hit) & (~bos)
    if np.any(missing):
        # Should not happen (every non-initial bigram has a left neighbour, at least <s>); fall back to raw counts.
        a2[missing] = c2[missing]
    return a2
