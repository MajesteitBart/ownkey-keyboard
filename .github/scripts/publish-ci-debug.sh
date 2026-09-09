#!/usr/bin/env bash
set -euo pipefail

# One mutable prerelease for merged PR builds. Versioned releases are never touched.
: "${BUILD_COMMIT:?}" "${VERSION:?}" "${PHONE_APK:?}" "${WEAR_APK:?}"
: "${GITHUB_REPOSITORY:?}" "${GITHUB_SERVER_URL:?}" "${GITHUB_RUN_ID:?}" "${RUNNER_TEMP:?}"
tag=ci-debug
repo="$GITHUB_REPOSITORY"
test -f "$PHONE_APK"
test -f "$WEAR_APK"

# Concurrency serializes publishers, but queued runs and manual reruns can be older.
# Never move the rolling release backwards or overwrite a tag changed outside this run.
previous=""
if remote_ref=$(git ls-remote --exit-code origin "refs/tags/$tag"); then
  previous=${remote_ref%%$'\t'*}
  git fetch --no-tags origin "refs/tags/$tag"
  current_commit=$(git rev-parse 'FETCH_HEAD^{commit}')
  if [[ "$BUILD_COMMIT" != "$current_commit" ]] && git merge-base --is-ancestor "$BUILD_COMMIT" "$current_commit"; then
    echo "Skipping an older CI build; $tag already points to a newer commit."
    exit 0
  fi
  if ! git merge-base --is-ancestor "$current_commit" "$BUILD_COMMIT"; then
    echo "Refusing to replace $tag with a commit from a different history." >&2
    exit 1
  fi
else
  status=$?
  # ls-remote exits 2 for an absent ref; authentication/network failures must stop publication.
  if [[ "$status" != 2 ]]; then exit "$status"; fi
fi

package_dir=$(mktemp -d "$RUNNER_TEMP/ownkey-ci-debug.XXXXXX")
trap 'rm -rf "$package_dir"' EXIT
cp "$PHONE_APK" "$package_dir/ownkey-phone-ci-debug.apk"
cp "$WEAR_APK" "$package_dir/ownkey-wear-ci-debug.apk"
cat > "$package_dir/notes.md" <<EOF
Automated CI debug prerelease from \`main\`, refreshed after PR merges.
Version: \`$VERSION\`
Commit: \`$BUILD_COMMIT\`
Includes: Phone + Wear debug APKs
Run: $GITHUB_SERVER_URL/$repo/actions/runs/$GITHUB_RUN_ID

This rolling prerelease replaces its APKs after PR merges; use a versioned release for normal installation.
EOF

# Query through a successful API response rather than treating every error as "not found".
release_id=$(gh api --paginate "repos/$repo/releases?per_page=100" \
  --jq '.[] | select(.tag_name == "ci-debug") | .id')
git push origin "$BUILD_COMMIT:refs/tags/$tag" "--force-with-lease=refs/tags/$tag:$previous"
if [[ -n "$release_id" ]]; then
  gh release upload "$tag" "$package_dir/ownkey-phone-ci-debug.apk" "$package_dir/ownkey-wear-ci-debug.apk" --clobber --repo "$repo"
  gh release edit "$tag" --title "Ownkey CI debug" --notes-file "$package_dir/notes.md" --prerelease --latest=false --repo "$repo"
else
  gh release create "$tag" "$package_dir/ownkey-phone-ci-debug.apk" "$package_dir/ownkey-wear-ci-debug.apk" \
    --verify-tag --title "Ownkey CI debug" --notes-file "$package_dir/notes.md" --prerelease --latest=false --repo "$repo"
fi
