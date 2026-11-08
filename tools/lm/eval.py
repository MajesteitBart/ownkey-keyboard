#!/usr/bin/env python3
"""Evaluate an Ownkey n-gram model (.bin) on sentences with a "words" list (jsonl).

Metrics per subset (the "lang" field, e.g. en / nl / mix):
- next-word top-1 / top-3 accuracy before the first letter (all positions, and sentence start only)
- perplexity over in-vocabulary targets, OOV rate
- simulated keystroke savings (KSR) with top-3 prefix completions: before each letter the typist looks at the
  three suggestions (top_next before the first letter, top_completions afterwards) and picks the target when it is
  there and picking saves a tap; picking costs 1 tap and includes the space. Letters are typed folded (no
  apostrophes or diacritics), as in tools/kbbench/PROTOCOL.md. taps_without = sum(len(folded word) + 1).
- completion hit rate (target in top 3) after 1, 2 and 3 typed letters (words longer than that many letters)

Usage:
  .venv/bin/python eval.py --model work/nl/model.bin --data ../kbbench/corpus/dev.jsonl --langs nl mix
  .venv/bin/python eval.py --model nl.bin --model2 en.bin --weight 0.8 --data dev.jsonl --langs mix  # mixture
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from lmtool import textnorm  # noqa: E402
from lmtool.binfmt import NgramModel  # noqa: E402

K = 3


class Single:
    def __init__(self, m: NgramModel):
        self.m = m
        self.fold_cache: dict[int, str] = {}

    def ids(self, words):
        return [self.m.word_id(w) for w in words]

    def ctx(self, ids, i):
        p1 = ids[i - 1] if i >= 1 else 0
        p2 = (ids[i - 2] if i >= 2 else 0) if i >= 1 else -1
        return p1, p2

    def folded(self, i):
        f = self.fold_cache.get(i)
        if f is None:
            f = textnorm.fold(self.m.word(i))
            self.fold_cache[i] = f
        return f

    def next_words(self, ids, i, k):
        p1, p2 = self.ctx(ids, i)
        return [self.folded(c) for c, _ in self.m.top_next(p1, p2, k)]

    def completions(self, ids, i, prefix, k):
        p1, p2 = self.ctx(ids, i)
        return [self.folded(c) for c, _ in self.m.top_completions(prefix, p1, p2, k)]

    def logprob(self, ids, i):
        if ids[i] < 0:
            return None
        p1, p2 = self.ctx(ids, i)
        return self.m.logprob(ids[i], p1, p2)


class Mixture:
    """Linear mixture w*P_a + (1-w)*P_b of two models over folded strings (for the mixed-language subset)."""

    def __init__(self, a: NgramModel, b: NgramModel, weight: float = 0.5):
        self.a, self.b = Single(a), Single(b)
        self.lw = (math.log(weight), math.log(1 - weight))

    def ids(self, words):
        return (self.a.ids(words), self.b.ids(words))

    def _merge(self, la, lb, k):
        score: dict[str, float] = {}
        for lst, lw in ((la, self.lw[0]), (lb, self.lw[1])):
            for f, lp in lst:
                prev = score.get(f)
                v = lw + lp
                score[f] = v if prev is None else max(prev, v) + math.log1p(math.exp(-abs(prev - v)))
        return [f for f, _ in sorted(score.items(), key=lambda x: -x[1])[:k]]

    def next_words(self, ids, i, k):
        out = []
        for s, sid in ((self.a, ids[0]), (self.b, ids[1])):
            p1, p2 = s.ctx(sid, i)
            out.append([(s.folded(c), lp) for c, lp in s.m.top_next(p1, p2, k)])
        return self._merge(out[0], out[1], k)

    def completions(self, ids, i, prefix, k):
        out = []
        for s, sid in ((self.a, ids[0]), (self.b, ids[1])):
            p1, p2 = s.ctx(sid, i)
            out.append([(s.folded(c), lp) for c, lp in s.m.top_completions(prefix, p1, p2, k)])
        return self._merge(out[0], out[1], k)

    def logprob(self, ids, i):
        la, lb = self.a.logprob(ids[0], i), self.b.logprob(ids[1], i)
        vals = [lw + x for x, lw in zip((la, lb), self.lw) if x is not None]
        if not vals:
            return None
        mx = max(vals)
        return mx + math.log(sum(math.exp(v - mx) for v in vals))


def evaluate(model, sentences: list[list[str]]) -> dict:
    n = top1 = top3 = 0
    s_n = s_top1 = s_top3 = 0
    lp_sum = 0.0
    lp_n = oov = 0
    taps = taps_without = 0
    comp = {1: [0, 0], 2: [0, 0], 3: [0, 0]}
    for words in sentences:
        words = [textnorm.normalize_word(w) for w in words]
        ids = model.ids(words)
        for i, w in enumerate(words):
            target = textnorm.fold(w)
            sugg = model.next_words(ids, i, K)
            hit1 = bool(sugg) and sugg[0] == target
            hit3 = target in sugg
            n += 1
            top1 += hit1
            top3 += hit3
            if i == 0:
                s_n += 1
                s_top1 += hit1
                s_top3 += hit3
            lp = model.logprob(ids, i)
            if lp is None:
                oov += 1
            else:
                lp_sum += lp
                lp_n += 1
            L = len(target)
            taps_without += L + 1
            hits = {0: hit3}

            def hit_at(k):
                if k not in hits:
                    hits[k] = target in model.completions(ids, i, target[:k], K)
                return hits[k]

            cost = L + 1
            for k in range(0, L):
                if hit_at(k):
                    cost = k + 1
                    break
            for k in (1, 2, 3):
                if L > k:
                    comp[k][0] += 1
                    comp[k][1] += hit_at(k)
            taps += cost
    return {
        "sentences": len(sentences), "words": n,
        "top1": round(top1 / max(n, 1), 4), "top3": round(top3 / max(n, 1), 4),
        "start_top1": round(s_top1 / max(s_n, 1), 4), "start_top3": round(s_top3 / max(s_n, 1), 4),
        "perplexity": round(math.exp(-lp_sum / max(lp_n, 1)), 1), "oov_rate": round(oov / max(n, 1), 4),
        "ksr": round(1 - taps / max(taps_without, 1), 4),
        "completion_top3_after_1_2_3": [round(comp[k][1] / max(comp[k][0], 1), 4) for k in (1, 2, 3)],
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--model2", default=None, help="second model: evaluate a linear mixture with --weight")
    ap.add_argument("--weight", type=float, default=0.5, help="mixture weight of --model")
    ap.add_argument("--data", required=True)
    ap.add_argument("--langs", nargs="*", default=None, help="subsets (lang field) to report")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()
    if os.path.basename(args.data) == "test.jsonl":
        sys.exit("refusing to evaluate on test.jsonl (test split is for headline numbers only)")
    rows = [json.loads(l) for l in open(args.data, encoding="utf-8") if l.strip()]
    m = NgramModel.load(args.model)
    model = Mixture(m, NgramModel.load(args.model2), args.weight) if args.model2 else Single(m)
    langs = args.langs or sorted({r.get("lang", "all") for r in rows})
    out = {}
    for lang in langs:
        sents = [r["words"] for r in rows if r.get("lang", "all") == lang]
        if sents:
            out[lang] = evaluate(model, sents)
    if args.json:
        print(json.dumps(out))
    else:
        for lang, r in out.items():
            print(f"{lang:5s} {json.dumps(r)}")


if __name__ == "__main__":
    main()
