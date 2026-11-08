#!/usr/bin/env bash
# Run the offline kbbench simulator (lib/prediction, Gradle task :lib:prediction:simulate).
#
# All arguments are passed to the simulator; see README.md or `run_sim.sh --help`. Paths are relative to the
# repository root. Gradle runs under a shared lock because several agents use the same worktree. `.txt` frequency
# lists and `.bin` n-gram models are both read by the simulator itself (no extra Gradle flags).
#
# Examples:
#   tools/kbbench/sim/run_sim.sh --touches both --out tools/kbbench/sim/results/dev.json
#   tools/kbbench/sim/run_sim.sh --mode noisy --set unknownWordLogProb=-18 --limit 50
#   tools/kbbench/sim/run_sim.sh --model nl=app/src/main/assets/ime/dict/ngram/nl.bin \
#       --model en=app/src/main/assets/ime/dict/ngram/en.bin --touches both
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$root"
lock="${OWNKEY_GRADLE_LOCK:-/tmp/ownkey-prediction-gradle.lock}"
args="$*"
exec flock "$lock" ./gradlew :lib:prediction:simulate --console=plain -q --args="$args"
