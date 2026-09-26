# Contributing to Ownkey

Ownkey Keyboard is a fork of [FlorisBoard](https://github.com/florisboard/florisboard) with its own issue tracker, releases and priorities. Report problems and send changes to this repository, not to FlorisBoard. Write issues and pull requests in English and follow the [code of conduct](CODE_OF_CONDUCT.md).

## Report a bug

Open an issue at [github.com/MajesteitBart/ownkey-keyboard/issues](https://github.com/MajesteitBart/ownkey-keyboard/issues). Include:

- the Ownkey version and where you got it (GitHub release, debug or beta build)
- your phone model and Android version
- the keyboard language you were typing in
- for dictation or rewrite problems, the provider and model, but never the API key
- the steps that trigger the bug, what you expected and what happened

When Ownkey crashes, its crash screen lets you copy the log to paste into the issue. You can also capture a log with `adb logcat`.

Logs and screenshots can contain what you typed or dictated. Remove personal text and API keys before you post them.

If the same bug also happens in FlorisBoard, mention that in the issue. Bugs in inherited code are often best fixed upstream first.

## Suggest a feature

Open an issue that describes the problem you want solved and how you type today. For larger changes, agree on the approach in the issue before writing code.

## Translations

Ownkey's own strings, such as the AI and Orukeet settings, are translated in `app/src/main/res/values-<locale>/ownkey.xml`. Changes to those files can go in a normal pull request.

The inherited `strings.xml` translations come from FlorisBoard's [Crowdin project](https://crowdin.florisboard.org). A check on pull requests rejects edits to translated `strings.xml` files, so send those translations to Crowdin instead.

## Code contributions

### Requirements

- JDK 17
- Android Studio, or the Android SDK for API 36 with the NDK and CMake versions from [`gradle/tools.versions.toml`](gradle/tools.versions.toml)
- [Rust](https://www.rust-lang.org/tools/install) installed through `rustup`, for the native library in `lib/native`. The build adds the Android targets itself.
- Git
- Python 3, only for the dictionary and dataset scripts in `tools/`

Gradle gets 4 GB of heap (`org.gradle.jvmargs=-Xmx4096m`), so a machine with 16 GB of RAM is comfortable next to Android Studio. CI builds on Linux, and the maintainer builds on Windows.

### Build and test

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :wear:assembleDebug
```

On Windows, use `.\gradlew.bat`. Debug builds install as `nl.bartvandermeeren.ownkey.debug`, next to a release install.

### What we look for

- Typing comes first. AI network work must never block typing, suggestions or opening the keyboard. Keep network calls off the main thread and cancel them when the keyboard closes.
- Private text stays private. Don't log typed text, transcripts, rewrite instructions or API keys, and don't add analytics. Cloud requests go only to the endpoint the user configured.
- User-facing copy uses "AI" for dictation and rewrite together. Provider names such as Voxtral, OpenAI or Anthropic are configuration details. Don't claim that Ownkey hosts AI or that cloud requests are processed on the phone.
- Autocorrect changes need benchmark numbers. `AutocorrectBenchmarkReportTest` runs with the unit tests and fails when a result drops below its floor. Put the before and after numbers in the pull request. When precision and recall conflict, precision wins. See the [autocorrect spec](.project/projects/autocorrect-engine/spec.md).
- Logic changes come with tests. UI changes come with a screenshot or short recording.
- Keep the Apache-2.0 license header and FlorisBoard attribution in source files.

### Pull requests

Branch from `main` and open the pull request against `main`. CI builds phone and Wear debug APKs for every pull request, and you can download them from the Actions run to test on a device. Describe what changed, why, and how you tested it, including which devices you used.
