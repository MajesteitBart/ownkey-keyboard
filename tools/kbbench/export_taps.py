"""Export the noisy typist's tap plan for offline simulation.

The device harness (bench.py run --mode noisy) and the offline simulator must see the same intent noise. This script
replays bench.py's seeded noise model against a keyboard calibration and writes, per sentence and word:
  intended word, pressed sequence (after the cognitive slip), literal letters hit (nearest-key approximation of the
  keyboard's hit test), and the tap offsets in key-pitch units (for coordinate-aware models).

Usage: python3 -I export_taps.py --split dev --calib calib/ownkey.json --seed s1 --out taps_dev_ownkey.json
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bench import cognitive_error, hit_key, load_corpus, noisy_offset, rng_for  # noqa: E402


class CalibratedKeyboard:
    def __init__(self, calib):
        self.keys = {k: tuple(v) for k, v in calib["keys"].items()}
        self.pitch = tuple(calib["pitch"])
        self.space = tuple(calib["space"])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--split", default="dev")
    ap.add_argument("--langs", default="en,nl,mix")
    ap.add_argument("--calib", required=True)
    ap.add_argument("--seed", default="s1")
    ap.add_argument("--sigma", type=float, default=0.20)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    kb = CalibratedKeyboard(json.load(open(args.calib, encoding="utf-8")))
    px, py = kb.pitch
    out = []
    for s in load_corpus(args.split, args.langs.split(","), 0):
        words = []
        for wi, word in enumerate(s["words"]):
            seq, slip = cognitive_error(word, rng_for(args.seed, s["id"], wi, "cog"))
            literal, offsets = [], []
            for ci, ch in enumerate(seq):
                dx, dy = noisy_offset(args.seed, s["id"], wi, ci, ch, args.sigma)
                x, y = kb.keys[ch]
                literal.append(hit_key(kb, x + dx * px, y + dy * py))
                offsets.append([round(dx, 4), round(dy, 4)])
            words.append({"word": word, "pressed": seq, "slip": slip, "literal": "".join(literal),
                          "intended_keys": seq, "offsets": offsets})
        out.append({"id": s["id"], "lang": s["lang"], "words": words})
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump({"split": args.split, "seed": args.seed, "sigma": args.sigma, "calib": args.calib,
                   "sentences": out}, f, ensure_ascii=False)
    n = sum(len(s["words"]) for s in out)
    bad = sum(1 for s in out for w in s["words"] if w["literal"] != "".join(c for c in w["word"].lower() if c.isalpha()))
    print(f"wrote {args.out}: {len(out)} sentences, {n} words, {bad} words whose literal differs from the plain word")


if __name__ == "__main__":
    main()
