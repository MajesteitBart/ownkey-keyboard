"""CLI: python -m lmtool <command> ...

  count   --lang nl --prep a.tsv [b.tsv ...] --out work/nl/counts [--vocab 150000 --min-count 5 --min-df 3]
          [--block-lines 1000 200 --partitions 32]
  build   --lang nl --counts work/nl/counts --out model.bin [pruning options]
          [--wordlist list.txt ... --valid-min-ppm 1]   (word validity, format v2; without --wordlist: v1)
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import sys
import time

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = os.path.dirname(os.path.dirname(HERE))
CORPUS = os.path.join(REPO, "tools", "kbbench", "corpus")
ATTRIBUTION = [
    "OPUS OpenSubtitles v2018 monolingual (opus.nlpl.eu; subtitles from http://www.opensubtitles.org/; "
    "P. Lison and J. Tiedemann, 2016, OpenSubtitles2016, LREC 2016)",
    "FineWeb / FineWeb-2 by Hugging Face (ODC-By 1.0; Common Crawl terms of use apply)",
]


def main() -> None:
    ap = argparse.ArgumentParser(prog="lmtool")
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("count")
    c.add_argument("--lang", required=True)
    c.add_argument("--prep", nargs="+", required=True)
    c.add_argument("--out", required=True)
    c.add_argument("--vocab", type=int, default=150000)
    c.add_argument("--min-count", type=int, default=5)
    c.add_argument("--min-df", type=int, default=3)
    c.add_argument("--workers", type=int, default=8)
    c.add_argument("--block-lines", type=int, nargs="+", default=None,
                   help="lines per dispersion block for --min-df: one value, or one per --prep file (default 200; "
                        "1000 for a subtitle sample of every line keeps a block at about one film)")
    c.add_argument("--partitions", type=int, default=16,
                   help="count partitions (more partitions lower the peak memory of counting and build)")
    c.add_argument("--no-exclusion", action="store_true")
    b = sub.add_parser("build")
    b.add_argument("--lang", required=True)
    b.add_argument("--counts", required=True)
    b.add_argument("--out", required=True)
    b.add_argument("--bigram-threshold", type=float, default=2.0)
    b.add_argument("--trigram-threshold", type=float, default=4.0)
    b.add_argument("--bigram-cap", type=int, default=64)
    b.add_argument("--bos-bigram-cap", type=int, default=255)
    b.add_argument("--trigram-cap", type=int, default=24)
    b.add_argument("--trigram-min-count", type=int, default=2)
    b.add_argument("--entry-bytes", type=int, default=3)
    b.add_argument("--meta", default=None, help="JSON file merged into the META section")
    b.add_argument("--wordlist", action="append", default=[],
                   help="curated word list for the validity flags (repeatable); writes format version 2")
    b.add_argument("--valid-min-ppm", type=float, default=1.0,
                   help="words at least this frequent (per million tokens) count as validated")
    args = ap.parse_args()

    if args.cmd == "count":
        from . import count

        excl = [] if args.no_exclusion else [os.path.join(CORPUS, "dev.jsonl"), os.path.join(CORPUS, "test.jsonl")]
        count.run(args.prep, args.out, excl, args.vocab, args.min_count, min_df=args.min_df, workers=args.workers,
                  block_lines=args.block_lines, npart=args.partitions)
    elif args.cmd == "build":
        from . import binfmt, kn

        cfg = kn.PruneConfig(
            bigram_threshold=args.bigram_threshold, trigram_threshold=args.trigram_threshold,
            bigram_cap=args.bigram_cap, bos_bigram_cap=args.bos_bigram_cap, trigram_cap=args.trigram_cap,
            trigram_min_count=args.trigram_min_count,
        )
        model = kn.estimate(args.counts, cfg, log=lambda s: print(s, file=sys.stderr, flush=True))
        meta = {"lang": args.lang, "format": 1, "smoothing": "interpolated modified Kneser-Ney, pruned to backoff",
                "built": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "prune": cfg.__dict__,
                "stats": model.stats, "attribution": ATTRIBUTION}
        cj = os.path.join(args.counts, "counts.json")
        if os.path.exists(cj):
            with open(cj) as f:
                meta["counts"] = json.load(f)
        if args.meta:
            with open(args.meta) as f:
                meta.update(json.load(f))
        valid = listed = None
        if args.wordlist:
            from . import validity

            valid, listed, vstats = validity.validity_from_counts(args.counts, args.wordlist, args.valid_min_ppm)
            meta["validity"] = vstats
            print("  validity: " + json.dumps(vstats), file=sys.stderr, flush=True)
        meta["format"] = binfmt.VERSION if valid is not None else binfmt.VERSION_1
        info = binfmt.write_model(args.out, args.counts, model, meta, entry_bytes=args.entry_bytes, valid=valid,
                                  listed=listed)
        print(json.dumps(info, indent=1), file=sys.stderr)


if __name__ == "__main__":
    main()
