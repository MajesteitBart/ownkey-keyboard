"""Pool noisy WER over several seeds: pairs are (seed, sentence); paired bootstrap 95% CI on Ownkey/Gboard."""
import json
import random
import sys

pairs = [p.split(",") for p in sys.argv[1:]]  # "gboard.jsonl,ownkey.jsonl" per seed


def load(p):
    return {json.loads(l)["sent"]["id"]: json.loads(l) for l in open(p, encoding="utf-8") if l.strip()}


units = []  # (lang, a_edits, b_edits, words)
for ga, ob in pairs:
    a, b = load(ga), load(ob)
    for sid in sorted(set(a) & set(b)):
        assert a[sid]["n"] == b[sid]["n"]
        units.append((a[sid]["sent"]["lang"], a[sid]["edits"], b[sid]["edits"], a[sid]["n"]))


def ratio(us):
    ea = sum(u[1] for u in us)
    eb = sum(u[2] for u in us)
    return (eb / ea) if ea else float("inf")


rnd = random.Random(7)
print(f"{'group':8} {'gboard':>8} {'ownkey':>8} {'B/A':>6}  95% CI        target")
for g in ("pooled", "en", "nl", "mix"):
    us = units if g == "pooled" else [u for u in units if u[0] == g]
    w = sum(u[3] for u in us)
    ea, eb = sum(u[1] for u in us), sum(u[2] for u in us)
    r = ratio(us)
    boots = sorted(ratio([us[rnd.randrange(len(us))] for _ in us]) for _ in range(2000))
    lo, hi = boots[50], boots[1949]
    verdict = ("MET (CI)" if hi <= 0.75 else "MET") if r <= 0.75 else "no"
    print(f"{g:8} {100*ea/w:7.2f}% {100*eb/w:7.2f}% {r:6.3f}  [{lo:.3f}, {hi:.3f}]  {verdict}  "
          f"(edits {ea} vs {eb}, words {w})")
