"""On-device keyboard benchmark driver (see PROTOCOL.md).

Usage:
  python3 -I bench.py calibrate --kb gboard
  python3 -I bench.py run --kb gboard --mode noisy --split dev --limit 20 --out results/
  python3 -I bench.py run --kb ownkey --mode clean --split test --out results/
"""
import argparse
import hashlib
import json
import math
import os
import random
import sys
import time
import unicodedata

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kbagent import Agent, IME_IDS  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
LETTERS = "abcdefghijklmnopqrstuvwxyz"
ROWS = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]


# ---------------------------------------------------------------- normalisation

def fold(s):
    """Case-, apostrophe- and diacritic-insensitive form used for all comparisons."""
    s = s.replace("’", "'").replace("'", "").lower()
    s = unicodedata.normalize("NFKD", s)
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = s.replace("ĳ", "ij")
    return s


def typed_form(word):
    """Letters the typist actually presses for an intended word (lowercase, no apostrophes, base letters)."""
    return "".join(c for c in fold(word) if c in LETTERS)


def tokens(text):
    out, cur = [], []
    for ch in text:
        if ch.isalpha() or ch in "'’":
            cur.append(ch)
        else:
            if cur:
                out.append("".join(cur))
                cur = []
    if cur:
        out.append("".join(cur))
    return [fold(t) for t in out if fold(t)]


def word_edit_distance(ref, hyp):
    n, m = len(ref), len(hyp)
    d = list(range(m + 1))
    for i in range(1, n + 1):
        prev, d[0] = d[0], i
        for j in range(1, m + 1):
            cur = d[j]
            d[j] = min(d[j] + 1, d[j - 1] + 1, prev + (ref[i - 1] != hyp[j - 1]))
            prev = cur
    return d[m]


def align_words(ref, hyp):
    """Levenshtein alignment returning list of (ref_word|None, hyp_word|None)."""
    n, m = len(ref), len(hyp)
    D = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(n + 1):
        D[i][0] = i
    for j in range(m + 1):
        D[0][j] = j
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            D[i][j] = min(D[i - 1][j] + 1, D[i][j - 1] + 1, D[i - 1][j - 1] + (ref[i - 1] != hyp[j - 1]))
    i, j, out = n, m, []
    while i > 0 or j > 0:
        if i > 0 and j > 0 and D[i][j] == D[i - 1][j - 1] + (ref[i - 1] != hyp[j - 1]):
            out.append((ref[i - 1], hyp[j - 1])); i -= 1; j -= 1
        elif i > 0 and D[i][j] == D[i - 1][j] + 1:
            out.append((ref[i - 1], None)); i -= 1
        else:
            out.append((None, hyp[j - 1])); j -= 1
    return out[::-1]


# ---------------------------------------------------------------- keyboard models

class Keyboard:
    """Geometry + suggestion reading for one IME, calibrated from its accessibility tree."""

    def __init__(self, name, agent):
        self.name = name
        self.a = agent
        self.keys = {}      # char -> (x, y) visual centre
        self.space = None
        self.pitch = None   # (px, py)
        self.strip_box = None  # (top, bottom) y-range of the suggestion row

    def calibrate(self):
        self.a.reset()
        time.sleep(0.8)
        nodes = self.a.ime_nodes(all=True)
        if self.name.startswith("gboard"):
            self._cal_gboard(nodes)
        else:
            self._cal_ownkey(nodes)
        missing = [c for c in LETTERS if c not in self.keys]
        if missing or not self.space:
            raise RuntimeError(f"calibration incomplete for {self.name}: missing {missing} space={self.space}")
        row_y = [sum(self.keys[c][1] for c in r) / len(r) for r in ROWS]
        px = sum(self.keys[r[i + 1]][0] - self.keys[r[i]][0] for r in ROWS for i in range(len(r) - 1)) / sum(len(r) - 1 for r in ROWS)
        py = (row_y[2] - row_y[0]) / 2
        self.pitch = (px, py)
        return self

    def _cal_gboard(self, nodes):
        for n in nodes:
            d = n.get("desc")
            b = n["b"]
            if n["cls"].endswith("TextView") and d and len(d) == 1 and d.lower() in LETTERS:
                self.keys[d.lower()] = ((b[0] + b[2]) / 2, (b[1] + b[3]) / 2)
            if d == "Spatie" or d == "Space":
                self.space = ((b[0] + b[2]) / 2, (b[1] + b[3]) / 2)
            if (n.get("id") or "").endswith("key_pos_header_access_points_menu"):
                self.strip_box = (b[1], b[3])
        # Gboard space FrameLayout includes a bottom margin; use the label row height of letters instead.
        if self.space and "b" in self.keys:
            self.space = (self.space[0], self.space[1] - 0.10 * (self.keys["b"][1] - self.keys["g"][1]))

    def _cal_ownkey(self, nodes):
        # Letter labels are TextViews with a single character; their parent View is the key.
        for n in nodes:
            t = n.get("text")
            b = n["b"]
            if n["cls"].endswith("TextView") and t and len(t) == 1 and t.lower() in LETTERS:
                self.keys[t.lower()] = ((b[0] + b[2]) / 2, (b[1] + b[3]) / 2)
        # Space: the key View whose child label is the language name; take the widest bottom-row View.
        letters_bottom = max(y for _, y in self.keys.values())
        best = None
        for n in nodes:
            b = n["b"]
            w = b[2] - b[0]
            if n["cls"].endswith("View") and b[1] > letters_bottom and 300 < w < 900:
                if best is None or w > best[2] - best[0]:
                    best = b
        if best:
            self.space = ((best[0] + best[2]) / 2, (best[1] + best[3]) / 2)
        else:
            # 0.11+ redesign: the space key has no View of its own; use its language label (e.g. "Dutch").
            for n in nodes:
                t, b = n.get("text") or "", n["b"]
                if n["cls"].endswith("TextView") and b[1] > letters_bottom and len(t) > 3 and t.isalpha():
                    self.space = ((b[0] + b[2]) / 2, (b[1] + b[3]) / 2)
                    break
        for n in nodes:
            if n["cls"].endswith("HorizontalScrollView"):
                self.strip_box = (n["b"][1], n["b"][3])
        if not self.strip_box:
            top_letter = min(y for _, y in self.keys.values())
            self.strip_box = (top_letter - 300, top_letter - 90)

    def suggestions(self):
        """Visible suggestion slots in display order: list of (text, (x, y))."""
        nodes = self.a.ime_nodes(all=False)
        top, bottom = self.strip_box
        out = []
        if self.name.startswith("gboard"):
            for n in nodes:
                b = n["b"]
                if not (b[1] >= top - 5 and b[3] <= bottom + 5):
                    continue
                if not n["cls"].endswith("TextView"):
                    continue
                label = n.get("desc") or n.get("text")
                if not label:
                    continue
                out.append((label, ((b[0] + b[2]) / 2, (b[1] + b[3]) / 2), b[0]))
        else:
            for n in nodes:
                b = n["b"]
                if not (b[1] >= top - 60 and b[3] <= bottom + 60):
                    continue
                if not n["cls"].endswith("TextView") or not n.get("text"):
                    continue
                vis_w = b[2] - b[0]
                if vis_w < 20:
                    continue  # clipped item at the scroll edge
                out.append((n["text"], ((b[0] + b[2]) / 2, (b[1] + b[3]) / 2), b[0]))
        out.sort(key=lambda t: t[2])
        return [(t, xy) for t, xy, _ in out]

    def to_dict(self):
        return {"name": self.name, "keys": self.keys, "space": self.space, "pitch": self.pitch, "strip_box": self.strip_box}


# ---------------------------------------------------------------- typists

def rng_for(*parts):
    h = hashlib.sha256("|".join(str(p) for p in parts).encode()).digest()
    return random.Random(int.from_bytes(h[:8], "big"))


def cognitive_error(word, r):
    """Return the letter sequence the typist intends to press for `word` (maybe with one cognitive slip)."""
    w = typed_form(word)
    if len(w) < 4 or r.random() >= 0.05:
        return w, None
    kind = r.choice(["transpose", "omit", "double"])
    if kind == "transpose":
        i = r.randrange(0, len(w) - 1)
        return w[:i] + w[i + 1] + w[i] + w[i + 2:], kind
    if kind == "omit":
        i = r.randrange(0, len(w))
        return w[:i] + w[i + 1:], kind
    i = r.randrange(0, len(w))
    return w[:i + 1] + w[i] + w[i + 1:], kind


def wait_text(agent, predicate, timeout=2.0, poll=0.05):
    t0 = time.time()
    while True:
        t = agent.text()
        if predicate(t) or time.time() - t0 > timeout:
            return t
        time.sleep(poll)


def stable_suggestions(kb, min_wait=0.15, settle=0.12, timeout=1.5):
    time.sleep(min_wait)
    t0 = time.time()
    prev = kb.suggestions()
    while time.time() - t0 < timeout:
        time.sleep(settle)
        cur = kb.suggestions()
        if [s for s, _ in cur] == [s for s, _ in prev]:
            return cur
        prev = cur
    return prev


def hit_key(kb, x, y):
    """Approximate which letter a tap lands on: row by nearest row centre, then nearest key in that row.

    Edge keys (q, a, p, l) extend to the screen edge on both keyboards. Taps are clamped by noisy_offset() so they
    never leave the letter block, so no other controls need modelling."""
    row_y = [sum(kb.keys[c][1] for c in r) / len(r) for r in ROWS]
    ri = min(range(3), key=lambda i: abs(row_y[i] - y))
    row = ROWS[ri]
    return min(row, key=lambda c: abs(kb.keys[c][0] - x))


EDGE_CLAMP = 0.45


def noisy_offset(seed, sid, wi, ci, ch, sigma):
    """Seeded tap offset in key-pitch units, clamped so the tap stays on the letter block (PROTOCOL.md)."""
    r = rng_for(seed, sid, wi, ci, "touch")
    dx, dy = r.gauss(0, sigma), r.gauss(0, sigma)
    if ch in ROWS[0]:
        dy = max(dy, -EDGE_CLAMP)   # above the top row is the suggestion strip
    if ch in ROWS[2]:
        dy = min(dy, EDGE_CLAMP)    # below the bottom letter row are space/symbol keys
    if ch == "z":
        dx = max(dx, -EDGE_CLAMP)   # shift
    if ch == "m":
        dx = min(dx, EDGE_CLAMP)    # backspace
    return dx, dy


def run_noisy(kb, sent, seed, sigma=0.20, gap=90, hold=40):
    a = kb.a
    a.reset()
    time.sleep(0.35)
    px, py = kb.pitch
    pts = []
    plan = []
    for wi, word in enumerate(sent["words"]):
        seq, slip = cognitive_error(word, rng_for(seed, sent["id"], wi, "cog"))
        literal = []
        for ci, ch in enumerate(seq):
            dx, dy = noisy_offset(seed, sent["id"], wi, ci, ch, sigma)
            x, y = kb.keys[ch]
            tx, ty = x + dx * px, y + dy * py
            pts.append((tx, ty))
            literal.append(hit_key(kb, tx, ty))
        plan.append({"word": word, "pressed": seq, "slip": slip, "literal": "".join(literal)})
        pts.append(kb.space)
        a.taps(pts, hold=hold, gap=gap)
        pts = []
        time.sleep(0.12)
    time.sleep(0.6)
    final = a.text()
    ref = [fold(w) for w in sent["words"]]
    hyp = tokens(final)
    edits = word_edit_distance(ref, hyp)
    # Per-word outcome classification via alignment.
    pairs = align_words(ref, hyp)
    literal_ok = [typed_form(p["word"]) == p["literal"] for p in plan]
    return {"final": final, "ref": ref, "hyp": hyp, "edits": edits, "n": len(ref), "plan": plan,
            "pairs": pairs, "literal_ok": literal_ok}


def run_clean(kb, sent, max_slots=3, log=None):
    a = kb.a
    a.reset()
    time.sleep(0.35)
    taps = 0
    needed = 0
    events = []
    desyncs = 0
    expected = ""
    for wi, word in enumerate(sent["words"]):
        target = fold(word)
        letters = typed_form(word)
        needed += len(letters) + 1
        typed = 0
        picked = False
        while True:
            remaining = len(letters) - typed + 1
            slots = stable_suggestions(kb)[:max_slots]
            hit = None
            for i, (s, xy) in enumerate(slots):
                if fold(s.strip()) == target:
                    hit = (i, s, xy)
                    break
            if hit and remaining > 1:
                a.tap(*hit[2])
                taps += 1
                picked = True
                events.append({"w": wi, "typed": typed, "slot": hit[0], "slots": [s for s, _ in slots]})
                break
            if typed == len(letters):
                break
            a.tap(*kb.keys[letters[typed]])
            taps += 1
            typed += 1
            if typed == 1 and wi == 0:
                pass
        if not picked:
            a.tap(*kb.space)
            taps += 1
        expected_words = [fold(w) for w in sent["words"][: wi + 1]]
        # A picked suggestion may leave a "phantom space" (inserted by the keyboard only when the next word starts),
        # so only require a real trailing space when the typist tapped space itself.
        need_space = not picked
        ok = lambda t: tokens(t) == expected_words and (t.endswith(" ") or not need_space)
        cur = wait_text(a, ok, timeout=1.2)
        if not ok(cur):
            desyncs += 1
            events.append({"w": wi, "desync": cur, "expected": " ".join(expected_words)})
            # Repair: put the intended text back (keyboard restarts input).
            a.reset(text=" ".join(sent["words"][: wi + 1]).capitalize() + " ")
            time.sleep(0.35)
    return {"taps": taps, "needed": needed, "desyncs": desyncs, "events": events, "final": a.text()}


# ---------------------------------------------------------------- main

def load_corpus(split, langs, limit, path=None):
    path = path or os.path.join(HERE, "corpus", f"{split}.jsonl")
    sents = [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
    sents = [s for s in sents if s["lang"] in langs]
    if limit:
        by_lang = {}
        for s in sents:
            by_lang.setdefault(s["lang"], []).append(s)
        sents = [s for lang in langs for s in by_lang.get(lang, [])[:limit]]
    return sents


def check_ime(agent, kb):
    cur = agent.adb("shell", "settings", "get", "secure", "default_input_method").strip()
    if cur != IME_IDS[kb]:
        raise RuntimeError(f"active IME changed to {cur}")
    kinds = [w["type"] for w in agent.windows()]
    if 2 not in kinds:
        raise RuntimeError("keyboard window not visible")


def ime_info(agent, kb):
    pkg = IME_IDS[kb].split("/")[0]
    out = agent.adb("shell", "dumpsys", "package", pkg)
    ver = next((l.split("=", 1)[1].strip() for l in out.splitlines() if "versionName=" in l), "?")
    return {"package": pkg, "version": ver}


def summarize(mode, rows):
    if mode == "noisy":
        n = sum(r["n"] for r in rows)
        e = sum(r["edits"] for r in rows)
        lit_ok = lit_bad = false_corr = fixed = 0
        for r in rows:
            # Only count per-word outcomes when the alignment is 1:1 for that word.
            ref_i = 0
            for (rw, hw) in r["pairs"]:
                if rw is None:
                    continue
                ok = r["literal_ok"][ref_i]
                if ok:
                    lit_ok += 1
                    false_corr += hw != rw
                else:
                    lit_bad += 1
                    fixed += hw == rw
                ref_i += 1
        return {"words": n, "edits": e, "wer": e / max(n, 1), "typed_correct_words": lit_ok,
                "false_correction_rate": false_corr / max(lit_ok, 1), "typo_words": lit_bad,
                "typo_fix_rate": fixed / max(lit_bad, 1)}
    taps = sum(r["taps"] for r in rows)
    need = sum(r["needed"] for r in rows)
    nw = sum(len(r["sent"]["words"]) for r in rows)
    nwp = sum(1 for r in rows for ev in r["events"] if ev.get("typed") == 0 and "slot" in ev)
    return {"taps": taps, "needed": need, "ksr": 1 - taps / max(need, 1), "words": nw,
            "next_word_hits": nwp, "next_word_hit_rate": nwp / max(nw, 1),
            "desyncs": sum(r["desyncs"] for r in rows)}


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("calibrate")
    c.add_argument("--kb", required=True)
    r = sub.add_parser("run")
    r.add_argument("--kb", required=True)
    r.add_argument("--mode", choices=["noisy", "clean"], required=True)
    r.add_argument("--split", default="dev")
    r.add_argument("--langs", default="en,nl,mix")
    r.add_argument("--limit", type=int, default=0)
    r.add_argument("--seed", default="s1")
    r.add_argument("--out", default=os.path.join(HERE, "results"))
    r.add_argument("--tag", default="")
    r.add_argument("--corpus", default=None, help="override corpus jsonl path")
    r.add_argument("--resume", default=None, help="append to this existing results jsonl, skipping finished sentences")
    args = ap.parse_args()

    agent = Agent().start()
    try:
        agent.set_ime(args.kb)
        kb = Keyboard(args.kb, agent).calibrate()
        if args.cmd == "calibrate":
            print(json.dumps(kb.to_dict(), indent=1))
            return
        info = ime_info(agent, args.kb)
        os.makedirs(os.path.join(HERE, "calib"), exist_ok=True)
        with open(os.path.join(HERE, "calib", f"{args.kb}_measured.json"), "w", encoding="utf-8") as cf:
            json.dump(kb.to_dict(), cf, indent=1)
        sents = load_corpus(args.split, args.langs.split(","), args.limit, args.corpus)
        os.makedirs(args.out, exist_ok=True)
        rows = []
        done = set()
        if args.resume:
            name = os.path.basename(args.resume)[: -len(".jsonl")]
            for line in open(args.resume, encoding="utf-8"):
                if line.strip():
                    row = json.loads(line)
                    rows.append(row)
                    done.add(row["sent"]["id"])
            print(f"resuming {name}: {len(done)} sentences already done", flush=True)
        else:
            stamp = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
            name = f"{stamp}_{args.kb}_{args.mode}_{args.split}{('_' + args.tag) if args.tag else ''}"
        with open(os.path.join(args.out, name + ".jsonl"), "a" if args.resume else "w", encoding="utf-8") as f:
            for i, s in enumerate(sents):
                if s["id"] in done:
                    continue
                t0 = time.time()
                res = None
                for attempt in range(3):
                    try:
                        check_ime(agent, args.kb)
                        if args.mode == "noisy":
                            res = run_noisy(kb, s, args.seed)
                        else:
                            res = run_clean(kb, s)
                        res["attempt"] = attempt
                        break
                    except Exception as e:  # interruption (call, notification, IME crash): relaunch and retry
                        print(f"  retry {s['id']} after error: {e}", flush=True)
                        time.sleep(3)
                        try:
                            agent.call("relaunch")
                            agent.set_ime(args.kb)
                            kb.calibrate()
                        except Exception as e2:
                            print(f"  relaunch failed: {e2}", flush=True)
                            agent.close()
                            agent = Agent().start()
                            agent.set_ime(args.kb)
                            kb.a = agent
                            kb.calibrate()
                if res is None:
                    print(f"  SKIP {s['id']}", flush=True)
                    continue
                res["sent"] = s
                res["secs"] = round(time.time() - t0, 2)
                rows.append(res)
                f.write(json.dumps(res, ensure_ascii=False) + "\n")
                f.flush()
                if args.mode == "noisy":
                    print(f"[{i+1}/{len(sents)}] {s['id']} edits={res['edits']}/{res['n']} :: {res['final']!r}", flush=True)
                else:
                    print(f"[{i+1}/{len(sents)}] {s['id']} taps={res['taps']}/{res['needed']} desync={res['desyncs']} :: {res['final']!r}", flush=True)
        summary = {"keyboard": args.kb, **info, "mode": args.mode, "split": args.split, "seed": args.seed,
                   "langs": args.langs, "n_sentences": len(rows), "calibration": kb.to_dict(),
                   "overall": summarize(args.mode, rows)}
        for lang in args.langs.split(","):
            sub_rows = [r for r in rows if r["sent"]["lang"] == lang]
            if sub_rows:
                summary[lang] = summarize(args.mode, sub_rows)
        with open(os.path.join(args.out, name + ".summary.json"), "w", encoding="utf-8") as f:
            json.dump(summary, f, indent=1, ensure_ascii=False)
        print(json.dumps({k: v for k, v in summary.items() if k != "calibration"}, indent=1))
    finally:
        agent.close()


if __name__ == "__main__":
    main()
