#!/usr/bin/env bash
# Grid-search EngineConfig on the dev split (the simulator refuses grids on test).
#
# Usage: tools/kbbench/sim/grid.sh <stage> [extra simulator args]
#   stage = autocorrect  unknown-word and known-word thresholds plus the generic insertion cost (noisy typist)
#           spelling     surface switches ("dont" -> "don't"); only the literal checks measure this
#           channel      spatial and edit costs (noisy typist)
#           display      completion and slot-order tunables (clean typist)
#           <anything>   only the extra args are used, e.g. grid.sh custom --grid alpha=0.8,1 --mode both
# Noisy metrics are pooled over every dev tap seed (tools/kbbench/taps_dev_ownkey.json plus
# tools/kbbench/sim/taps/taps_dev_ownkey_s*.json). Every row also runs the held-out literal checks
# (literal_checks.jsonl); rows whose keep rate is below the floor (--keep-floor, default 0.95) rank last. Accept a row
# only if it "beats" the current config: lower noisy WER on every seed and a clean KSR that is not lower (the KSR is
# shared by all seeds, so it is compared once). touchSigma is never tuned here
# (the synthetic taps match the touch model exactly).
# Results go to tools/kbbench/sim/results/grid_<stage>.json; the table printed at the end is sorted by the
# objective (combined = KSR - WER, or --objective ksr|wer).
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
stage="${1:-autocorrect}"
shift || true
case "$stage" in
  autocorrect)
    grid=(--mode noisy --touches off
      --grid unknownWordLogProb=-19,-16,-13
      --grid tauUnknown=0.5,0.8
      --grid tauUnknownShort=0.7,0.9
      --grid minUnknownAutocorrectLength=2,3
      --grid insertionCost=5,8,10) ;;
  spelling)
    grid=(--mode noisy --touches off
      --grid surfaceSwitchMargin=1,2,3) ;;
  channel)
    grid=(--mode noisy
      --grid keySigma=0.35,0.45,0.55
      --grid alpha=0.8,1.0,1.2
      --grid omissionCost=3.5,4.5
      --grid insertionCost=6.5,8) ;;
  display)
    grid=(--mode clean
      --grid displaySavingsWeight=0,0.5,1,1.5
      --grid completionLogPenalty=0,-0.5
      --grid negligibleProb=0.001,0.003,0.01) ;;
  *)
    grid=() ;;
esac
mkdir -p "$here/results"
exec "$here/run_sim.sh" "${grid[@]}" --threads "${GRID_THREADS:-4}" --out "tools/kbbench/sim/results/grid_${stage}.json" "$@"
