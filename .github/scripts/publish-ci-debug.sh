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
if [[ -n "$release_id" ]]; then
  # Upload both replacements before touching the existing APKs or tag. Unique names
  # let retries proceed even if a failed upload left a partial asset behind.
  suffix="$GITHUB_RUN_ID-$(basename "$package_dir")"
  phone_stage="ownkey-ci-staging-phone-$suffix.apk"
  wear_stage="ownkey-ci-staging-wear-$suffix.apk"
  cp "$PHONE_APK" "$package_dir/$phone_stage"
  cp "$WEAR_APK" "$package_dir/$wear_stage"
  gh release upload "$tag" "$package_dir/$phone_stage" "$package_dir/$wear_stage" --repo "$repo"
  # Claim the tag before replacing public names. A rejected lease leaves the other
  # publisher's APKs and notes untouched; our staging files are safe to discard later.
  git push origin "$BUILD_COMMIT:refs/tags/$tag" "--force-with-lease=refs/tags/$tag:$previous"

  replace_asset() {
    local staged=$1 stable=$2 old_id new_id
    new_id=$(gh api --paginate "repos/$repo/releases/$release_id/assets?per_page=100" \
      --jq ".[] | select(.name == \"$staged\" and .state == \"uploaded\") | .id")
    : "${new_id:?Replacement APK is not uploaded}"
    old_id=$(gh api --paginate "repos/$repo/releases/$release_id/assets?per_page=100" \
      --jq ".[] | select(.name == \"$stable\") | .id")
    if [[ -n "$old_id" ]]; then
      # Keep the previous bytes recoverable until both new names, tag and notes are ready.
      gh api --method PATCH "repos/$repo/releases/assets/$old_id" \
        -f "name=ownkey-ci-previous-$suffix-$stable" > /dev/null
    fi
    gh api --method PATCH "repos/$repo/releases/assets/$new_id" -f "name=$stable" > /dev/null
  }
  replace_asset "$phone_stage" ownkey-phone-ci-debug.apk
  replace_asset "$wear_stage" ownkey-wear-ci-debug.apk
  gh release edit "$tag" --title "Ownkey CI debug" --notes-file "$package_dir/notes.md" --draft=false --prerelease --latest=false --repo "$repo"

  # Clean up backups and abandoned staged files only after successful publication.
  obsolete_ids=$(gh api --paginate "repos/$repo/releases/$release_id/assets?per_page=100" \
    --jq '.[] | select(.name | startswith("ownkey-ci-staging-") or startswith("ownkey-ci-previous-")) | .id')
  while IFS= read -r asset_id; do
    if [[ -n "$asset_id" ]]; then
      gh api --method DELETE "repos/$repo/releases/assets/$asset_id"
    fi
  done <<< "$obsolete_ids"
else
  git push origin "$BUILD_COMMIT:refs/tags/$tag" "--force-with-lease=refs/tags/$tag:$previous"
  gh release create "$tag" "$package_dir/ownkey-phone-ci-debug.apk" "$package_dir/ownkey-wear-ci-debug.apk" \
    --verify-tag --title "Ownkey CI debug" --notes-file "$package_dir/notes.md" --prerelease --latest=false --repo "$repo"
fi
