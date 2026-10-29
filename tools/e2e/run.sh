#!/usr/bin/env bash
# Ownkey end-to-end suite: prepares an emulator with adb, then runs the Maestro flows in
# tools/e2e/maestro/ one at a time. See tools/e2e/README.md.
#
#   tools/e2e/run.sh [--serial emulator-5554] [--apk <path>] [--flows all|<name,...>] [--out tmp/e2e-out]
#                    [--repeat N] [--run N] [--reset] [--no-install]
#   tools/e2e/run.sh deeplink <path>     # open ui://florisboard/<path> on the device and exit
#   tools/e2e/run.sh restore-onboarding  # mark setup done again after an interrupted 00-setup
#
# Exits non-zero when a flow fails or the app crashes during a flow.
set -uo pipefail
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FLOW_DIR="$ROOT/tools/e2e/maestro"

SERIAL="emulator-5554"
APK="app/build/outputs/apk/debug/app-x86_64-debug.apk"
FLOWS="all"
OUT="tmp/e2e-out"
REPEAT=1
FIRST_RUN=1
RESET=0
INSTALL=1

PKG="nl.bartvandermeeren.ownkey.debug"
IME="$PKG/dev.patrickgold.florisboard.FlorisImeService"
SETTINGS_ACTIVITY="$PKG/dev.patrickgold.florisboard.app.FlorisAppActivity"
PREFS_FILE="jetpref_datastore/florisboard-app-prefs.jetpref"

log() { printf '[e2e] %s\n' "$*" >&2; }
die() { log "error: $*"; exit 2; }
adb_s() { adb -s "$SERIAL" "$@"; }
shell() { adb_s shell "$@" | tr -d '\r'; }

find_maestro() {
  if [ -n "${MAESTRO:-}" ]; then echo "$MAESTRO"; return; fi
  if command -v maestro >/dev/null 2>&1; then command -v maestro; return; fi
  [ -x "$HOME/.maestro/bin/maestro" ] && { echo "$HOME/.maestro/bin/maestro"; return; }
  die "maestro not found; install Maestro 2.5.1 or set MAESTRO=<path>"
}

# Opens a settings deep link. The activity only routes VIEW intents that carry CATEGORY_BROWSABLE
# (anything else is treated as an extension import), and the explicit component avoids a chooser
# when the release or beta build is installed too.
deeplink() {
  local path="${1#/}"
  # The path ends up inside a device shell command, so only accept route characters.
  [[ "$path" =~ ^[A-Za-z0-9/_.?=\&%-]+$ ]] || die "invalid deep-link path: $path"
  shell "am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'ui://florisboard/$path' -n $SETTINGS_ACTIVITY" >/dev/null
}

grant_permissions() {
  shell pm grant "$PKG" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
  shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
}

# Maestro, agent-device and the onboarding reset can all switch the keyboard, so this runs
# before every flow and fails loudly when Ownkey doesn't stick.
set_ime() {
  local current
  for _ in 1 2 3; do
    shell ime enable "$IME" >/dev/null 2>&1
    shell ime set "$IME" >/dev/null 2>&1
    current="$(shell settings get secure default_input_method)"
    [ "$current" = "$IME" ] && return 0
    sleep 1
  done
  log "could not select $IME (current: $current)"
  return 1
}

# Only one UiAutomation client can be connected. agent-device's snapshot helper keeps one open,
# which stops the Maestro driver from starting.
free_uiautomation() {
  if shell pidof com.callstack.agentdevice.snapshothelper >/dev/null 2>&1; then
    log "stopping agent-device snapshot helper so the Maestro driver can connect"
    shell am force-stop com.callstack.agentdevice.snapshothelper >/dev/null 2>&1
    sleep 1
  fi
}

prep_device() {
  log "preparing $SERIAL"
  grant_permissions
  shell settings put secure show_ime_with_hard_keyboard 1 >/dev/null
  shell settings put global window_animation_scale 0 >/dev/null
  shell settings put global transition_animation_scale 0 >/dev/null
  shell settings put global animator_duration_scale 0 >/dev/null
  shell cmd uimode night yes >/dev/null
  shell svc power stayon true >/dev/null 2>&1 || true
  shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  shell wm dismiss-keyguard >/dev/null 2>&1 || true
  set_ime || die "Ownkey keyboard is not selectable on $SERIAL"
}

# Puts onboarding back to its first-run state without `pm clear`, which would also delete the
# downloaded on-device model, saved keys and languages. Clears the setup flag and the stored
# permission answers, and revokes the two runtime permissions so Setup asks for them again.
reset_onboarding_soft() {
  log "resetting onboarding (setup flag and permission answers)"
  local other
  other="$(shell ime list -s | grep -v "^$PKG/" | head -n1)"
  [ -n "$other" ] && shell ime set "$other" >/dev/null 2>&1
  shell am force-stop "$PKG"
  # One string, because adb hands it to the device shell, where ';' and '$' are special.
  shell "run-as $PKG sed -i \
    -e 's/^b;internal__is_ime_set_up_v2;true\$/b;internal__is_ime_set_up_v2;false/' \
    -e '/^s;internal__microphone_permission_state;/d' \
    -e '/^s;internal__notification_permission_state;/d' \
    -e '/^b;internal__microphone_permission_requested;/d' \
    $PREFS_FILE" || die "run-as failed; the onboarding reset needs a debuggable build (or use --reset)"
  shell "run-as $PKG grep -q '^b;internal__is_ime_set_up_v2;false\$' $PREFS_FILE" \
    || die "couldn't clear the setup flag in $PREFS_FILE"
  for perm in RECORD_AUDIO POST_NOTIFICATIONS; do
    shell pm revoke "$PKG" "android.permission.$perm" >/dev/null 2>&1 || true
    shell pm clear-permission-flags "$PKG" "android.permission.$perm" user-set user-fixed >/dev/null 2>&1 || true
  done
  set_ime
}

# Undoes reset_onboarding_soft when the onboarding flow didn't finish, so later flows (and anyone
# else using the device) don't land on Setup.
restore_onboarding() {
  shell "run-as $PKG grep -q '^b;internal__is_ime_set_up_v2;true\$' $PREFS_FILE" && return 0
  log "onboarding didn't finish; marking setup done again"
  local other
  other="$(shell ime list -s | grep -v "^$PKG/" | head -n1)"
  [ -n "$other" ] && shell ime set "$other" >/dev/null 2>&1
  shell am force-stop "$PKG"
  grant_permissions
  shell "run-as $PKG sed -i \
    -e '/^b;internal__is_ime_set_up_v2;/d' \
    -e '/^s;internal__microphone_permission_state;/d' \
    -e '/^s;internal__notification_permission_state;/d' \
    $PREFS_FILE"
  shell "run-as $PKG sh -c \"printf '%s\\n' 'b;internal__is_ime_set_up_v2;true' \
    's;internal__microphone_permission_state;\\\"GRANTED\\\"' \
    's;internal__notification_permission_state;\\\"GRANTED\\\"' >> $PREFS_FILE\""
  set_ime
}

# Full wipe for --reset. Deletes all app data, including a downloaded on-device model.
reset_app_data() {
  log "clearing all app data (pm clear)"
  shell pm clear "$PKG" >/dev/null
  grant_permissions
  set_ime
}

# Deep links that must keep resolving after the redesign: "<path> <kind>:<value>".
DEEPLINK_CHECKS=(
  "settings/home id:home-ai"
  "settings/localization id:languages-add"
  "settings/localization/subtype/add id:add-language-search"
  "settings/localization/subtype/new id:subtype-language"
  "settings/theme id:appearance-mode"
  "settings/keyboard id:keyboard-space-neighbor"
  "settings/keyboard/input-feedback id:feedback-key-sounds"
  "settings/smartbar id:keyboard-space-neighbor"
  "settings/typing id:typing-autocorrect"
  "settings/dictionary id:saved-words-yours"
  "settings/gestures text:Swipe on letters"
  "settings/clipboard id:clipboard-history"
  "settings/media id:clipboard-history"
  "settings/voxtral id:dictation-option-cloud"
  "settings/voxtral/rewrite-styles id:rewrite-style-add"
  "settings/privacy id:privacy-incognito"
  "settings/other id:privacy-incognito"
  "settings/other/restore id:restore-select-file"
  "settings/about id:about-version"
)

MAESTRO_BIN=""
RESULTS=()
FAILED=0

run_maestro() {
  local name="$1" flow="$2" outdir="$3"; shift 3
  mkdir -p "$outdir" "$OUT/junit"
  "$MAESTRO_BIN" --udid "$SERIAL" test --format junit \
    --output "$OUT/junit/$name.xml" \
    --test-output-dir "$outdir" \
    --debug-output "$outdir/debug" \
    "$@" "$flow" >"$outdir/maestro.log" 2>&1
}

# Runs one flow; retries only when the Maestro driver itself fails to start (an infrastructure
# problem), never on a failed assertion.
run_flow_file() {
  local name="$1" flow="$2" outdir="$3"; shift 3
  local rc attempt
  for attempt in 1 2; do
    free_uiautomation
    run_maestro "$name" "$flow" "$outdir" "$@"; rc=$?
    if [ $rc -ne 0 ] && grep -qE 'did not start up in time|UNAVAILABLE|Connection refused' "$outdir/maestro.log" \
       && ! grep -qE '\[Failed\]' "$outdir/maestro.log"; then
      log "$name: Maestro driver didn't start, retrying ($attempt)"
      sleep 3; continue
    fi
    break
  done
  return $rc
}

run_deeplink_checks() {
  local outdir="$1" run="$2" rc=0 entry path sel kind value i=0
  mkdir -p "$outdir"
  for entry in "${DEEPLINK_CHECKS[@]}"; do
    path="${entry%% *}"; sel="${entry#* }"; kind="${sel%%:*}"; value="${sel#*:}"
    i=$((i + 1))
    shell am force-stop "$PKG"
    set_ime >/dev/null
    deeplink "$path"
    if ! run_flow_file "30-deeplink-$(printf '%02d' "$i")-run$run" "$FLOW_DIR/helpers/assert-$kind.yaml" "$outdir/$i" \
        -e "EXPECT=$value" -e "SHOT=deeplink-$i"; then
      log "deep link ui://florisboard/$path did not show $sel"
      rc=1
    fi
  done
  return $rc
}

crash_check() {
  local since="$1" crashes
  crashes="$(shell "logcat -d -b crash -T '$since'" 2>/dev/null | grep -A20 "Process: $PKG" || true)"
  if [ -n "$crashes" ]; then
    printf '%s\n' "$crashes"
    return 1
  fi
  return 0
}

merge_junit() {
  local report="$OUT/report.xml"
  {
    echo "<?xml version='1.0' encoding='UTF-8'?>"
    echo "<testsuites name=\"ownkey-e2e\">"
    for f in "$OUT"/junit/*.xml; do
      [ -f "$f" ] || continue
      sed -n '/<testsuite /,/<\/testsuite>/p' "$f" \
        | sed "s/<testsuite name=\"[^\"]*\"/<testsuite name=\"$(basename "$f" .xml)\"/"
    done
    echo "</testsuites>"
  } >"$report"
  log "junit report: $report"
}

list_flows() {
  local f name
  for f in "$FLOW_DIR"/[0-9][0-9]-*.yaml; do
    name="$(basename "$f" .yaml)"
    echo "$name"
  done
  echo "30-deeplinks"
}

select_flows() {
  if [ "$FLOWS" = "all" ]; then list_flows; return; fi
  local want available
  available="$(list_flows)"
  IFS=',' read -ra want <<<"$FLOWS"
  for w in "${want[@]}"; do
    local match
    match="$(printf '%s\n' "$available" | grep -E "^${w}(-|$)" | head -n1)"
    [ -n "$match" ] || die "unknown flow: $w"
    echo "$match"
  done
}

usage() { sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; }

main() {
  if [ "${1:-}" = "deeplink" ]; then
    [ -n "${2:-}" ] || die "usage: run.sh deeplink <path> [--serial <serial>]"
    [ "${3:-}" = "--serial" ] && SERIAL="$4"
    deeplink "$2"; exit $?
  fi
  if [ "${1:-}" = "restore-onboarding" ]; then
    [ "${2:-}" = "--serial" ] && SERIAL="$3"
    restore_onboarding; exit $?
  fi
  while [ $# -gt 0 ]; do
    case "$1" in
      --serial) SERIAL="$2"; shift 2 ;;
      --apk) APK="$2"; shift 2 ;;
      --flows) FLOWS="$2"; shift 2 ;;
      --out) OUT="$2"; shift 2 ;;
      --repeat) REPEAT="$2"; shift 2 ;;
      --run) FIRST_RUN="$2"; shift 2 ;;
      --reset) RESET=1; shift ;;
      --no-install) INSTALL=0; shift ;;
      -h|--help) usage; exit 0 ;;
      *) die "unknown argument: $1" ;;
    esac
  done

  cd "$ROOT" || exit 2
  case "$OUT" in /*|[A-Za-z]:*) ;; *) OUT="$ROOT/$OUT" ;; esac
  command -v adb >/dev/null 2>&1 || die "adb not found"
  MAESTRO_BIN="$(find_maestro)"
  adb_s get-state >/dev/null 2>&1 || die "device $SERIAL is not connected"
  mkdir -p "$OUT/junit"

  if [ "$INSTALL" = 1 ]; then
    [ -f "$APK" ] || die "APK not found: $APK (build it with ./gradlew :app:assembleDebug)"
    log "installing $APK"
    adb_s install -r -g "$APK" >/dev/null || die "install failed"
  fi
  prep_device

  local flows run name flow outdir start rc
  flows="$(select_flows)" || exit $?
  [ -n "$flows" ] || die "no flows selected"
  for run in $(seq "$FIRST_RUN" $((FIRST_RUN + REPEAT - 1))); do
    for name in $flows; do
      outdir="$OUT/run$run/$name"
      rm -rf "$outdir"; mkdir -p "$outdir"
      start="$(shell "date '+%m-%d %H:%M:%S.000'")"
      case "$name" in
        00-*) if [ "$RESET" = 1 ]; then reset_app_data; else reset_onboarding_soft; fi ;;
      esac
      # A fresh process so every flow starts on Home with an empty preview field. Stopping the
      # package also stops Ownkey's keyboard service, after which Android picks another keyboard,
      # so Ownkey is selected again straight after.
      shell am force-stop "$PKG"
      set_ime || true
      log "run $run: $name"
      if [ "$name" = "30-deeplinks" ]; then
        rm -f "$OUT"/junit/30-deeplink-*-run"$run".xml
        run_deeplink_checks "$outdir" "$run"; rc=$?
      else
        flow="$FLOW_DIR/$name.yaml"
        rm -f "$OUT/junit/$name-run$run.xml"
        run_flow_file "$name-run$run" "$flow" "$outdir"; rc=$?
      fi
      case "$name" in
        00-*) restore_onboarding; grant_permissions; set_ime || true ;;
      esac
      if ! crash_check "$start" >"$outdir/crash.txt"; then
        log "$name: app crash recorded in $outdir/crash.txt"
        rc=1
      else
        rm -f "$outdir/crash.txt"
      fi
      if [ $rc -eq 0 ]; then RESULTS+=("PASS  run$run  $name"); else RESULTS+=("FAIL  run$run  $name  ($outdir)"); FAILED=$((FAILED + 1)); fi
      log "${RESULTS[-1]}"
    done
  done

  merge_junit
  printf '\n'
  printf '%s\n' "${RESULTS[@]}"
  [ "$FAILED" -eq 0 ] || { log "$FAILED flow run(s) failed"; exit 1; }
  log "all flows passed"
}

main "$@"
