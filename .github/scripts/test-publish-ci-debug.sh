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
        stale:built:previous | update:previous:built | draft:previous:built | upload-error:previous:built | api-error:previous:built) return 0 ;;
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
    case "$*" in
      *--method*) return 0 ;;
      *startswith*) printf '101\n102\n' ;;
      *ownkey-ci-staging-phone*) printf '201\n' ;;
      *ownkey-ci-staging-wear*) printf '202\n' ;;
      *ownkey-phone-ci-debug*) printf '101\n' ;;
      *ownkey-wear-ci-debug*) printf '102\n' ;;
      *) if [[ "$scenario" != create ]]; then printf '123\n'; fi ;;
    esac
  fi
  if [[ "$scenario:$1:$2" == upload-error:release:upload ]]; then return 1; fi
  return 0
}

for scenario in create update draft stale diverged network-error api-error upload-error; do
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
    update | draft)
      test "$result" = 0
      grep -q 'gh release upload ci-debug.*ownkey-ci-staging-phone.*ownkey-ci-staging-wear' "$fixture/calls"
      ! grep -q -- '--clobber' "$fixture/calls"
      grep -q 'gh release edit ci-debug' "$fixture/calls"
      grep -q -- '--draft=false --prerelease --latest=false' "$fixture/calls"
      grep -q -- '--force-with-lease=refs/tags/ci-debug:previous' "$fixture/calls"
      ! grep -q 'gh release create' "$fixture/calls"
      # Existing assets are retained as backups; deletion is strictly after publication.
      grep -q 'name=ownkey-ci-previous-' "$fixture/calls"
      edit_line=$(grep -n 'gh release edit' "$fixture/calls" | cut -d: -f1)
      delete_line=$(grep -n -- '--method DELETE' "$fixture/calls" | head -1 | cut -d: -f1)
      test "$delete_line" -gt "$edit_line"
      ;;
    upload-error)
      test "$result" != 0
      ! grep -Eq 'git push|--method PATCH|--method DELETE|gh release edit' "$fixture/calls"
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
