"""Compare benchmark result files and check the 25% target (see PROTOCOL.md).

usage:
  python3 -I compare.py --a results/<gboard>_noisy_test.jsonl --b results/<ownkey>_noisy_test.jsonl [--b more.jsonl]
  (several files per side are merged, e.g. a two-pass run with one language per pass)

Prints per-language and pooled metrics for both sides, the ratio B/A, and a sentence-level paired bootstrap 95%
interval for the ratio. For noisy runs the target is WER_B <= 0.75 * WER_A; for clean runs KSR_B >= 1.25 * KSR_A.
Only sentences present on both sides are compared.
"""
import argparse
import json
import random


def load(paths):
    rows = {}
    mode = None
    for p in paths:
        for line in open(p, encoding="utf-8"):
            if not line.strip():
                continue
            r = json.loads(line)
            m = "noisy" if "edits" in r else "clean"
            if mode and m != mode:
                raise SystemExit(f"mixed modes in {paths}")
            mode = m
            rows[r["sent"]["id"]] = r
    return mode, rows


def metric(mode, rows):
    if mode == "noisy":
        n = sum(r["n"] for r in rows)
        return sum(r["edits"] for r in rows) / max(n, 1)
    need = sum(r["needed"] for r in rows)
    return 1 - sum(r["taps"] for r in rows) / max(need, 1)


def extra(mode, rows):
    if mode == "noisy":
        return {"words": sum(r["n"] for r in rows), "edits": sum(r["edits"] for r in rows)}
    nw = sum(len(r["sent"]["words"]) for r in rows)
    nwp = sum(1 for r in rows for ev in r["events"] if ev.get("typed") == 0 and "slot" in ev)
    return {"taps": sum(r["taps"] for r in rows), "needed": sum(r["needed"] for r in rows),
            "next_word_hit": round(nwp / max(nw, 1), 4), "desyncs": sum(r["desyncs"] for r in rows)}


def bootstrap_ratio(mode, a, b, ids, iters=2000, seed=7):
    rnd = random.Random(seed)
    out = []
    for _ in range(iters):
        sample = [ids[rnd.randrange(len(ids))] for _ in ids]
        ma = metric(mode, [a[i] for i in sample])
        mb = metric(mode, [b[i] for i in sample])
        if ma > 0:
            out.append(mb / ma)
    out.sort()
    return out[int(0.025 * len(out))], out[int(0.975 * len(out))]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--a", action="append", required=True, help="baseline (Gboard) result jsonl; repeatable")
    ap.add_argument("--b", action="append", required=True, help="challenger (Ownkey) result jsonl; repeatable")
    ap.add_argument("--json", default=None, help="write the comparison as json here")
    args = ap.parse_args()
    ma, a = load(args.a)
    mb, b = load(args.b)
    if ma != mb:
        raise SystemExit("modes differ")
    mode = ma
    ids = sorted(set(a) & set(b))
    missing = (set(a) | set(b)) - set(ids)
    name = "WER" if mode == "noisy" else "KSR"
    report = {"mode": mode, "metric": name, "sentences": len(ids), "not_in_both": len(missing), "groups": {}}
    groups = [("pooled", ids)] + [(lang, [i for i in ids if a[i]["sent"]["lang"] == lang]) for lang in ("en", "nl", "mix")]
    print(f"{name} ({mode}), {len(ids)} paired sentences, {len(missing)} unpaired skipped")
    print(f"{'group':8} {'A':>8} {'B':>8} {'B/A':>7} {'95% CI of B/A':>18}  target  extra")
    for gname, gids in groups:
        if not gids:
            continue
        va = metric(mode, [a[i] for i in gids])
        vb = metric(mode, [b[i] for i in gids])
        ratio = vb / va if va else float("inf")
        lo, hi = bootstrap_ratio(mode, a, b, gids)
        ok = ratio <= 0.75 if mode == "noisy" else ratio >= 1.25
        sure = hi <= 0.75 if mode == "noisy" else lo >= 1.25
        verdict = "MET" + (" (CI)" if sure else "") if ok else "no"
        print(f"{gname:8} {va:8.4f} {vb:8.4f} {ratio:7.3f} [{lo:6.3f}, {hi:6.3f}]  {verdict:9} "
              f"A={extra(mode, [a[i] for i in gids])} B={extra(mode, [b[i] for i in gids])}")
        report["groups"][gname] = {"a": va, "b": vb, "ratio": ratio, "ci95": [lo, hi], "met": ok, "met_ci": sure,
                                   "n": len(gids)}
    if args.json:
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=1)


if __name__ == "__main__":
    main()
