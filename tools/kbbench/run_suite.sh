#!/usr/bin/env bash
# Runs benchmark jobs sequentially: run_suite.sh "kb mode split [extra args]" ...
set -u
cd "$(dirname "$0")"
for job in "$@"; do
  set -- $job
  kb=$1; mode=$2; split=$3; shift 3
  echo "=== $(date -u +%FT%TZ) START $kb $mode $split $*"
  python3 -I bench.py run --kb "$kb" --mode "$mode" --split "$split" "$@" 2>&1 | grep -vE '^\s*$'
  echo "=== $(date -u +%FT%TZ) END $kb $mode $split"
done
