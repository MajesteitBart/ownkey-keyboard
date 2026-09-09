# Android CI and releases

| Trigger | Build | GitHub release |
| --- | --- | --- |
| PR opened, updated, or reopened against `main` | Phone and Wear debug APKs | None; download the Actions artifacts |
| PR merged into `main` | Phone and Wear debug APKs from the merged commit | Refresh the single `ci-debug` prerelease |
| PR closed without merging | None | None |
| Direct push to `main` | None | None |
| Manual workflow run | Selected variant and optional Wear build | None; download the Actions artifacts |
| Push of a `v*` tag | Release APKs and AABs | Existing versioned tag release workflow |

The rolling release is titled **Ownkey CI debug**. Its `ci-debug` tag advances to the
published merge commit, and its two APK assets keep stable filenames. It is always
a prerelease and never becomes the latest stable release. Concurrent merge builds
are serialized, and older reruns cannot replace a newer published build.

Production-signed `android-v*` releases remain a separate manual publishing process.
CI's release variant can fall back to a debug signing key, so its output must not
be presented as production-signed without verification.

Existing historical CI releases and tags are not removed by the workflow.

Run the publisher's mocked regression checks locally with:

```bash
bash .github/scripts/test-publish-ci-debug.sh
```

These cover first publication, replacement of existing assets, stale and divergent
commits, and network/API failures without writing to GitHub.
