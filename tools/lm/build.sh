#!/usr/bin/env bash
# Rebuild Ownkey's static n-gram models (en, nl) from public downloads.
#
#   tools/lm/build.sh            # both languages (the shipped "large" profile)
#   tools/lm/build.sh nl         # one language
#   FORCE_PREP=1 tools/lm/build.sh   # redo normalisation even when prepared files exist
#   PROFILE=small tools/lm/build.sh  # the earlier 7 MB models (less data, 150k nl words, heavier pruning; README.md)
#   OUT_DIR=/some/dir WORK_DIR=/scratch tools/lm/build.sh nl
#
# Steps per language: download OPUS OpenSubtitles v2018 mono (resumable) -> sample + normalise -> fetch FineWeb(-2)
# samples over HTTPS (parquet row groups) -> count (excluding kbbench dev/test sentences) -> Kneser-Ney + pruning
# -> word validity flags from a curated word list (OpenTaal / SCOWL, see SOURCES.md)
# -> <OUT_DIR>/<lang>.bin (format v2) -> evaluation on tools/kbbench/corpus/dev.jsonl.
# The default profile, large, keeps its prepared text and counts in tools/lm/work/large/ (WORK_DIR), writes
# app/src/main/assets/ime/dict/ngram/ (OUT_DIR) and regenerates the Kotlin test fixtures. The small profile keeps its
# data in tools/lm/work/<lang>/ and writes tools/lm/work/small/models/; either profile regenerates the fixtures only
# when OUT_DIR is the assets directory.
# Large intermediate data lives in tools/lm/work/ (gitignored). Peak memory per process stays below ~3 GB (large)
# or ~2 GB (small).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
WORK="$HERE/work"
PY="$HERE/.venv/bin/python"
ASSETS="$REPO/app/src/main/assets/ime/dict/ngram"
DEV="$REPO/tools/kbbench/corpus/dev.jsonl"
OPUS="https://object.pouta.csc.fi/OPUS-OpenSubtitles/v2018/mono"
PROFILE="${PROFILE:-large}"

# Per-language settings of the small profile (chosen on dev.jsonl, see tools/lm/README.md); large overrides some.
declare -A SUB_EVERY=([en]=25 [nl]=5)          # keep every k-th subtitle line (en ~95M, nl ~119M tokens)
declare -A WEB_STRIDE=([en]=9 [nl]=37)         # parquet row-group stride of the web samples
declare -A BIGRAM_T=([en]=20 [nl]=25)          # pruning thresholds (weighted log-ratio)
declare -A TRIGRAM_T=([en]=50 [nl]=60)
WEB_TOKENS=30000000                            # first web sample (row groups 0, stride, 2*stride, ...)
declare -A WEB_EXTRA=([en]="" [nl]="")         # more web samples, "first_row_group:tokens" with the same stride
declare -A SUB_BLOCK=([en]=200 [nl]=200)       # lines per dispersion block of the subtitle sample (--min-df)
declare -A VOCAB=([en]=150000 [nl]=150000)
PWORK="$WORK"                                  # prepared text and counts of this profile
OUT="${OUT_DIR:-$WORK/small/models}"
COUNT_OPTS=()

case "$PROFILE" in
    small) ;;
    large)
        # The shipped models: about 0.81 B (nl) and 0.57 B (en) training tokens; nl 38.6 MB + en 31.6 MB (README.md).
        SUB_EVERY=([en]=5 [nl]=1)              # en ~476M, nl ~596M subtitle tokens
        SUB_BLOCK=([en]=200 [nl]=1000)         # a block stays about one film (1000 raw subtitle lines)
        WEB_EXTRA=([en]="3:60000000" [nl]="6:45000000 12:45000000 18:45000000 24:45000000")  # web en 90M, nl 210M
        VOCAB=([en]=150000 [nl]=250000)
        BIGRAM_T=([en]=12 [nl]=16)
        TRIGRAM_T=([en]=30 [nl]=40)
        PWORK="${WORK_DIR:-$WORK/large}"
        OUT="${OUT_DIR:-$ASSETS}"
        COUNT_OPTS=(--partitions 32 --workers 6)
        ;;
    *) echo "unknown PROFILE=$PROFILE (large or small)" >&2; exit 2 ;;
esac

# Curated word lists for the validity flags (lmtool/validity.py). Downloaded once into work/wordlists/.
OPENTAAL_REV=b250510dda431785f962019167d1415198ff3905   # OpenTaal wordlist 2.20.23 (2023-03-10)
declare -A WORDLIST_URL=(
    [nl]="https://raw.githubusercontent.com/OpenTaal/opentaal-wordlist/$OPENTAAL_REV/wordlist.txt"
    [en]="https://app.aspell.net/create?max_size=70&spelling=US&spelling=GBs&spelling=GBz&variant_level=1&diacritic=both&special=hacker&encoding=utf-8&format=inline&download=wordlist"
)
declare -A WORDLIST_FILE=([nl]=opentaal/wordlist.txt [en]=scowl/en_size70_US_GBs_GBz_v1.txt)
# Words not in the list count as valid from this many occurrences per million training tokens (README.md).
VALID_MIN_PPM=1

langs=("$@")
[ ${#langs[@]} -gt 0 ] || langs=(en nl)

if [ ! -x "$PY" ]; then
    uv venv "$HERE/.venv"
    uv pip install --python "$PY" -r "$HERE/requirements.txt"
fi
cd "$HERE"
mkdir -p "$OUT" "$WORK/logs"
LOGS="$WORK/logs"
[ "$PROFILE" = small ] || LOGS="$PWORK/logs"
mkdir -p "$LOGS"

for lang in "${langs[@]}"; do
    echo "== $lang ($PROFILE)"
    dl="$WORK/download/opensubtitles-v2018"
    mkdir -p "$dl" "$PWORK/$lang/prep"
    if [ ! -f "$dl/$lang.txt.gz.done" ]; then
        curl -fSL -C - --retry 5 -o "$dl/$lang.txt.gz" "$OPUS/$lang.txt.gz"
        touch "$dl/$lang.txt.gz.done"
    fi
    prep="$PWORK/$lang/prep"
    if [ -n "${FORCE_PREP:-}" ] || [ ! -f "$prep/opensub.tsv" ]; then
        "$PY" -m lmtool.prepare opensub --lang "$lang" --src "$dl/$lang.txt.gz" --every "${SUB_EVERY[$lang]}" \
            --out "$prep/opensub.tsv"
    fi
    if [ -n "${FORCE_PREP:-}" ] || [ ! -f "$prep/web.tsv" ]; then
        "$PY" -m lmtool.prepare fineweb --lang "$lang" --tokens "$WEB_TOKENS" --rg-stride "${WEB_STRIDE[$lang]}" \
            --out "$prep/web.tsv"
    fi
    preps=("$prep/opensub.tsv" "$prep/web.tsv")
    blocks=("${SUB_BLOCK[$lang]}" 200)
    for spec in ${WEB_EXTRA[$lang]}; do
        start="${spec%%:*}"
        f="$prep/web.s$start.tsv"
        if [ -n "${FORCE_PREP:-}" ] || [ ! -f "$f" ]; then
            "$PY" -m lmtool.prepare fineweb --lang "$lang" --tokens "${spec#*:}" --rg-start "$start" \
                --rg-stride "${WEB_STRIDE[$lang]}" --out "$f"
        fi
        preps+=("$f")
        blocks+=(200)
    done
    count_opts=("${COUNT_OPTS[@]}")
    [ "$PROFILE" = small ] || count_opts+=(--block-lines "${blocks[@]}")
    "$PY" -m lmtool count --lang "$lang" --prep "${preps[@]}" --out "$PWORK/$lang/counts" \
        --vocab "${VOCAB[$lang]}" --min-count 5 --min-df 3 "${count_opts[@]}" 2>&1 | tee "$LOGS/count_$lang.log"
    wl="$WORK/wordlists/${WORDLIST_FILE[$lang]}"
    if [ ! -s "$wl" ]; then
        mkdir -p "$(dirname "$wl")"
        curl -fSL --retry 5 -o "$wl.tmp" "${WORDLIST_URL[$lang]}"
        mv "$wl.tmp" "$wl"
    fi
    "$PY" -m lmtool build --lang "$lang" --counts "$PWORK/$lang/counts" --out "$OUT/$lang.bin" \
        --bigram-threshold "${BIGRAM_T[$lang]}" --trigram-threshold "${TRIGRAM_T[$lang]}" \
        --bigram-cap 5000 --bos-bigram-cap 5000 --trigram-cap 255 --entry-bytes 3 \
        --wordlist "$wl" --valid-min-ppm "$VALID_MIN_PPM" 2>&1 | tee "$LOGS/build_$lang.log"
    if [ -f "$DEV" ]; then
        "$PY" eval.py --model "$OUT/$lang.bin" --data "$DEV" --langs "$lang" mix | tee "$LOGS/eval_$lang.log"
    fi
done

# Test fixtures for the Kotlin reader (tiny models + Python-reference answers, also for the production assets).
if [ "$(cd "$OUT" && pwd)" = "$(cd "$ASSETS" && pwd)" ]; then
    "$PY" make_fixture.py
    "$PY" make_fixture.py --assets "${langs[@]}"
else
    echo "Models in $OUT (not the app assets): Kotlin test fixtures not regenerated."
fi
