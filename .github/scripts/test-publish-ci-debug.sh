#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
export RUNNER_TEMP="$fixture" PHONE_APK="$fixture/phone.apk" WEAR_APK="$fixture/wear.apk"
export BUILD_COMMIT=built VERSION=0.7.0 GITHUB_REPOSITORY=example/ownkey
export GITHUB_SERVER_URL=https://github.com GITHUB_RUN_ID=42
touch "$PHONE_APK" "$WEAR_APK"

git() {
  printf 'git %s\n' "$*" >> "$fixture/calls"
  case "$1" in
    ls-remote)
      case "$scenario" in
        create) return 2 ;;
        network-error) return 128 ;;
        *) printf 'previous\trefs/tags/ci-debug\n' ;;
      esac ;;
    fetch) return 0 ;;
    rev-parse) printf 'previous\n' ;;
    merge-base)
      case "$scenario:$3:$4" in
        stale:built:previous | update:previous:built | api-error:previous:built) return 0 ;;
        *) return 1 ;;
      esac ;;
    push) return 0 ;;
    *) echo "Unexpected git call" >&2; return 1 ;;
  esac
}
gh() {
  printf 'gh %s\n' "$*" >> "$fixture/calls"
  if [[ "$1" == api ]]; then
    if [[ "$scenario" == api-error ]]; then return 1; fi
    if [[ "$scenario" != create ]]; then printf '123\n'; fi
  fi
  return 0
}

for scenario in create update stale diverged network-error api-error; do
  : > "$fixture/calls"
  set +e
  (source "$script_dir/publish-ci-debug.sh") > "$fixture/output" 2>&1
  result=$?
  set -e
  case "$scenario" in
    create)
      test "$result" = 0
      grep -q 'gh release create ci-debug' "$fixture/calls"
      grep -q -- '--force-with-lease=refs/tags/ci-debug:$' "$fixture/calls"
      grep -q -- '--prerelease --latest=false' "$fixture/calls"
      ;;
    update)
      test "$result" = 0
      grep -q 'gh release upload ci-debug.*ownkey-phone-ci-debug.apk.*ownkey-wear-ci-debug.apk.*--clobber' "$fixture/calls"
      grep -q 'gh release edit ci-debug' "$fixture/calls"
      grep -q -- '--force-with-lease=refs/tags/ci-debug:previous' "$fixture/calls"
      ! grep -q 'gh release create' "$fixture/calls"
      ;;
    stale)
      test "$result" = 0
      ! grep -Eq 'git push|gh release' "$fixture/calls"
      ;;
    *)
      test "$result" != 0
      ! grep -Eq 'git push|gh release' "$fixture/calls"
      ;;
  esac
  echo "PASS: $scenario"
done
