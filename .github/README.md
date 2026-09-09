# Android CI and releases

| Trigger | Build | GitHub release |
| --- | --- | --- |
| PR opened, updated, or reopened against `main` | Phone and Wear debug APKs | None; download the Actions artifacts |
| PR merged into `main` | Phone and Wear debug APKs from the current `main` tip | Refresh the single `ci-debug` prerelease |
| PR closed without merging | None | None |
| Direct push to `main` | None | None |
| Manual workflow run | Selected variant and optional Wear build | None; download the Actions artifacts |
| Push of a `v*` tag | Release APKs and AABs | Existing versioned tag release workflow |

The rolling release is titled **Ownkey CI debug**. Its `ci-debug` tag advances to the
published build commit, and its two APK assets keep stable filenames. It is always
a prerelease and never becomes the latest stable release. Concurrent merge builds
are serialized. Each surviving run checks out `main` when it starts, so an old
rerun that replaces a newer pending run still builds the latest code. Older built
commits cannot replace a newer published build.
Manual runs have separate concurrency groups so requesting multiple variants never
discards an earlier pending manual build.

Production-signed `android-v*` releases remain a separate manual publishing process.
CI's release variant can fall back to a debug signing key, so its output must not
be presented as production-signed without verification.

Existing historical CI releases and tags are not removed by the workflow.

Run the publisher's mocked regression checks locally with:

```bash
bash .github/scripts/test-publish-ci-debug.sh
```

These cover first publication, staged replacement of existing assets, recovery of
an unpublished draft, stale and divergent commits, and network/API/upload/lease failures
without writing to GitHub. APK replacements upload and claim the tag before old assets are renamed
to backups; backups are deleted only after the tag and published release are updated.
