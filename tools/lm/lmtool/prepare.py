"""Stage 1: raw sources -> prepared token files.

Output format (UTF-8, one raw line or paragraph per line): sentence segments separated by TAB, tokens
separated by a single space, original casing kept (casing statistics are computed later).

  python -m lmtool.prepare opensub --lang nl --src <nl.txt.gz> --every 5 --out <prep/opensub.tsv>
  python -m lmtool.prepare fineweb --lang nl --tokens 30000000 --out <prep/web.tsv>
"""

from __future__ import annotations

import argparse
import gzip
import json
import multiprocessing as mp
import os
import sys
import time

from . import textnorm

FINEWEB_URLS = {
    # FineWeb-2 (ODC-By 1.0), Dutch train shard 0 (row groups of 1000 documents), revision pinned.
    "nl": ("https://huggingface.co/datasets/HuggingFaceFW/fineweb-2/resolve/"
           "af9c13333eb981300149d5ca60a8e9d659b276b9/data/nld_Latn/train/000_00000.parquet"),
    # FineWeb sample-10BT (ODC-By 1.0), shard 0, revision pinned.
    "en": ("https://huggingface.co/datasets/HuggingFaceFW/fineweb/resolve/"
           "9bb295ddab0e05d785b879661af7260fed5140fc/sample/10BT/000_00000.parquet"),
}

_LANG = "en"
_SUBTITLE = True


def _init(lang: str, subtitle: bool) -> None:
    global _LANG, _SUBTITLE
    _LANG = lang
    _SUBTITLE = subtitle


def _process_batch(lines: list[str]) -> tuple[str, int, int]:
    out = []
    ntok = 0
    for line in lines:
        segs = textnorm.segments_original_case(line, _LANG, _SUBTITLE)
        if not segs:
            continue
        ntok += sum(len(s) for s in segs)
        out.append("\t".join(" ".join(s) for s in segs))
    return ("\n".join(out) + "\n") if out else "", len(lines), ntok


def _opensub_batches(src: str, every: int, offset: int, batch: int = 20000):
    buf = []
    with gzip.open(src, "rt", encoding="utf-8", errors="replace") as f:
        for i, line in enumerate(f):
            if i % every != offset:
                continue
            buf.append(line.rstrip("\n"))
            if len(buf) >= batch:
                yield buf
                buf = []
    if buf:
        yield buf


def run_opensub(args) -> None:
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    ctx = mp.get_context("fork")
    t0 = time.time()
    nlines = ntok = 0
    tmp = args.out + ".partial"
    with ctx.Pool(args.workers, initializer=_init, initargs=(args.lang, True)) as pool, open(tmp, "w", encoding="utf-8") as out:
        for text, n, t in pool.imap(_process_batch, _opensub_batches(args.src, args.every, args.offset), chunksize=1):
            out.write(text)
            nlines += n
            ntok += t
            if nlines % 2_000_000 < 20000:
                print(f"  opensub {args.lang}: {nlines:,} sampled lines, {ntok:,} tokens, {time.time() - t0:.0f}s",
                      file=sys.stderr, flush=True)
    os.replace(tmp, args.out)
    meta = {"source": "opensubtitles-v2018", "src": os.path.basename(args.src), "every": args.every,
            "offset": args.offset, "sampled_lines": nlines, "tokens": ntok}
    with open(args.out + ".json", "w") as f:
        json.dump(meta, f, indent=1)
    print(json.dumps(meta), file=sys.stderr)


def _fineweb_docs(url: str, rg_start: int, rg_stride: int, max_rgs: int, stats: dict):
    import pyarrow.parquet as pq
    from .httpfile import HttpRangeFile

    f = HttpRangeFile(url)
    pf = pq.ParquetFile(f)
    n = pf.metadata.num_row_groups
    picked = list(range(rg_start, n, rg_stride))[:max_rgs]
    for k, rg in enumerate(picked):
        table = pf.read_row_group(rg, columns=["text"])
        stats["row_groups"].append(rg)
        stats["bytes_fetched"] = f.bytes_fetched
        yield table.column("text").to_pylist()


def run_fineweb(args) -> None:
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    url = args.url or FINEWEB_URLS[args.lang]
    ctx = mp.get_context("fork")
    stats = {"row_groups": [], "bytes_fetched": 0}
    t0 = time.time()
    ndocs = ntok = 0
    tmp = args.out + ".partial"
    with ctx.Pool(args.workers, initializer=_init, initargs=(args.lang, False)) as pool, open(tmp, "w", encoding="utf-8") as out:
        for docs in _fineweb_docs(url, args.rg_start, args.rg_stride, args.max_row_groups, stats):
            # Paragraph = line; batch by documents.
            lines = []
            for d in docs:
                lines.extend(p for p in d.split("\n") if p.strip())
            batches = [lines[i:i + 5000] for i in range(0, len(lines), 5000)]
            for text, _, t in pool.imap(_process_batch, batches):
                out.write(text)
                ntok += t
            ndocs += len(docs)
            print(f"  fineweb {args.lang}: {ndocs:,} docs, {ntok:,} tokens, {stats['bytes_fetched'] / 1e6:.0f} MB fetched, "
                  f"{time.time() - t0:.0f}s", file=sys.stderr, flush=True)
            if ntok >= args.tokens:
                break
    os.replace(tmp, args.out)
    meta = {"source": "fineweb" if args.lang == "en" else "fineweb-2", "url": url, "documents": ndocs,
            "tokens": ntok, "row_groups": stats["row_groups"], "bytes_fetched": stats["bytes_fetched"]}
    with open(args.out + ".json", "w") as f:
        json.dump(meta, f, indent=1)
    print(json.dumps({k: v for k, v in meta.items() if k != "row_groups"}), file=sys.stderr)


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("opensub")
    a.add_argument("--lang", required=True)
    a.add_argument("--src", required=True)
    a.add_argument("--every", type=int, default=5)
    a.add_argument("--offset", type=int, default=0)
    a.add_argument("--out", required=True)
    a.add_argument("--workers", type=int, default=10)
    b = sub.add_parser("fineweb")
    b.add_argument("--lang", required=True)
    b.add_argument("--url", default=None)
    b.add_argument("--tokens", type=int, default=30_000_000)
    b.add_argument("--rg-start", type=int, default=0)
    b.add_argument("--rg-stride", type=int, default=37)
    b.add_argument("--max-row-groups", type=int, default=400)
    b.add_argument("--out", required=True)
    b.add_argument("--workers", type=int, default=10)
    args = ap.parse_args()
    if args.cmd == "opensub":
        run_opensub(args)
    else:
        run_fineweb(args)


if __name__ == "__main__":
    main()
