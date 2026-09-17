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

## Phone ABI artifacts and Orukeet

Phone APK builds produce `arm64-v8a`, `armeabi-v7a`, `x86_64` and `x86` files, without a
universal APK. Actions artifacts and versioned releases contain all four. The stable
`ownkey-phone-ci-debug.apk` link is the arm64 phone build; emulator users take the
x86_64 Actions artifact. Wear packaging is unchanged.

Build standalone APKs and the Play bundle in separate invocations:

```bash
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease -Pownkey.apkSplits=false
```

AGP 9 resource shrinking cannot combine ABI APK outputs and an AAB in one invocation.
The bundle includes each ABI and Play generates device-specific delivery. Keep APK
outputs until `.github/scripts/package-phone.py` has copied every ABI using AGP's
output metadata. CI publishes `SHA256SUMS` with versioned assets.

Orukeet weights are separate, pinned data assets in the `orukeet-v0.1.0-int8` prerelease.
They are never in an APK/AAB. Model and runtime notices ship in the app. Model files,
partials, active-version pointers, download-consent tokens, and temporary local audio
use Android's `noBackupFilesDir`; platform backup rules and the manual backup export
allowlist exclude this directory. A restored `orukeet` preference without files fails
closed and requires a new explicit download and activation.

Local inference is enabled in internal debug, beta and benchmark builds. Public
release builds keep it unavailable until the physical arm64 quality/performance and
supported-device policy gates in the delivery spec pass. Before enabling it publicly,
revise Play copy to distinguish **on-device dictation** from **cloud dictation and
rewrite**. Voice rewrite still sends the instruction transcript and selected text to
the configured rewrite endpoint. Do not describe every AI operation as local.
