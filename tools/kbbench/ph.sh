#!/usr/bin/env bash
# Small adb helpers for driving the physical test phone during keyboard benchmarking.
# Usage: ph.sh shot [name] | ph.sh ui [filter] | ph.sh tap X Y | ph.sh tapt "text" | ph.sh sh <cmd...>
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
S="${PHONE_SERIAL:-$(adb devices -l | awk '/model:SM_S928B/{print $1}')}"
cmd="${1:-}"; shift || true
dump() { adb -s "$S" shell uiautomator dump /sdcard/ph_ui.xml >/dev/null; adb -s "$S" shell cat /sdcard/ph_ui.xml; }
case "$cmd" in
  shot)
    out="/tmp/ph_${1:-shot}.png"
    adb -s "$S" exec-out screencap -p > "$out"
    python3 -I -c "
from PIL import Image; import sys
im = Image.open(sys.argv[1]); im = im.resize((im.width//2, im.height//2)); im.save(sys.argv[2])
" "$out" "${out%.png}_s.png" 2>/dev/null || cp "$out" "${out%.png}_s.png"
    echo "${out%.png}_s.png (half-scale: multiply coords by 2)"
    ;;
  ui) dump | python3 -I "$HERE/uidump.py" list "${1:-}" ;;
  tap) adb -s "$S" shell input tap "$1" "$2" ;;
  tapt)
    xy=$(dump | python3 -I "$HERE/uidump.py" find "$1") || { echo "not found: $1" >&2; exit 1; }
    adb -s "$S" shell input tap $xy; echo "tapped '$1' at $xy"
    ;;
  sh) adb -s "$S" shell "$@" ;;
  *) echo "usage: ph.sh shot|ui|tap|tapt|sh" >&2; exit 2;;
esac
