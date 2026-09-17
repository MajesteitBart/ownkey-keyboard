# Emulator setup probe

Date: 2026-09-15
Scope: Install and boot Android emulators for the Orukeet probe.
Status: Both virtual devices were boot-verified during setup. The later [runtime probe](emulator-runtime.md) ran on the 16 KB guest. The ordinary guest is currently stopped.

## Uncertainty tested

Can the current development environment run hardware-accelerated Android emulators so functional probe work can start before physical phones are available?

## Experiment shape

Install a user-local JDK and Android SDK, create ordinary and 16 KB API 36 x86_64 virtual devices, verify KVM access, boot them, and inspect guest properties and a system UI through ADB. This is environment readiness, not an Orukeet inference or performance result.

## Evidence observed

- JDK: Eclipse Temurin 21.0.12.1+1; downloaded package SHA-256 checked against the published checksum.
- Android command-line tools build 15859902; SHA-256 checked against the official download page.
- Emulator 37.1.11, platform-tools 37.0.1, platform API 36 revision 2, build-tools 36.0.0 installed.
- Virtual devices: `ownkey-api36` with the API 36 AOSP x86_64 image revision 2; `ownkey-api36-16k` with the Google APIs 16 KB x86_64 image revision 7. Both use a 6 GiB configured guest RAM limit, two vCPUs, and SwiftShader graphics.
- `emulator -accel-check` confirmed KVM version 12 was usable after access was granted.
- Standard emulator: `sys.boot_completed=1`, API 36, ABI `x86_64`, `getconf PAGE_SIZE=4096`. Settings launch returned success; UI hierarchy and screenshot inspection confirmed the Settings screen rendered.
- Persistent KVM group membership is confirmed in the account database. The current agent process still has its original supplementary groups.
- 16 KB emulator: the user launched it with the KVM group. `emulator-5556` reports `sys.boot_completed=1`, API 36, ABI `x86_64`, and `getconf PAGE_SIZE=16384`. Settings launch returned success; the resumed activity, UI hierarchy, and screenshot inspection confirmed the rendered Settings screen.
- Both guests were online at setup completion. The ordinary `emulator-5554` later stopped; `emulator-5556` remains running and is open in the T3 Device panel. The group-refresh limitation does not block use of that running guest.

## Touched surfaces

- User-local SDK/JDK, virtual-device data, shell environment snippet, and emulator launcher outside the repository.
- This evidence file, the draft spec/plan, and a project update. No production app files changed.

## Footguns

- Initial sudo calls require interactive authentication in this environment. The user granted temporary device access; that ACL was subsequently replaced. The user then added the account to the KVM group, which is confirmed.
- This installation has neither `sg` nor `newgrp`. The earlier expectation that the agent could refresh group access through `sg` was incorrect. A fresh login or an authenticated launch with the KVM group is required. The launcher now checks that `sg` exists before attempting to use it.
- The current command-line tools deprecate `sdkmanager` in favor of `android sdk`; package installation through the compatibility command succeeded.
- Installed system images omit optional device metadata that `avdmanager` warns about. Built-in `medium_phone` definitions still created the virtual devices successfully.
- Run an emulator as a managed foreground process for agent sessions. A detached shell invocation did not keep it running here.
- Explicitly set `ANDROID_USER_HOME` to the existing `.android` directory and `ANDROID_AVD_HOME` to its `avd` subdirectory. Without the latter, the installed `avdmanager` returned an empty inventory in this session even though `emulator -list-avds` found both guests. Both commands now list the same two devices.
- Emulator ABI/page-size success is not arm64 validation. Desktop acceleration and configured guest RAM are not evidence for phone latency, memory headroom, or thermal gates.

## Repeatable local commands

The initial setup created an opt-in shell snippet and launcher. After `avdmanager` failed in the user terminal and T3, the repair added the snippet to the user's `.bashrc` and `.profile`, preserving backups. Wrappers for Java and Android commands in the existing user-local PATH make already-running clients discover the SDK. The installed device hub defaults to `~/Library/Android/sdk` when its inherited environment lacks `ANDROID_HOME`; an alias to the actual SDK fixes discovery in the current hub. Normal `avdmanager list avd` and T3 device discovery now work. The remaining Apple `xcrun` warning reflects unavailable iOS tools on Linux. The launcher checks device access and can use `sg kvm` if that helper is installed. On this machine, use a fresh login or an authenticated launch with the KVM group because neither group-refresh helper is installed.

```bash
source "$HOME/.config/ownkey-android/env.sh"
emulator -accel-check
"$HOME/.local/bin/ownkey-emulator" -no-window -no-audio -no-snapshot -no-metrics -port 5554
```

Use `--16k` as the launcher's first argument and port 5556 for the second image. Omit `-no-window` when a visible emulator window is desired. Run ADB commands in a second shell with the same environment snippet sourced.

```bash
adb devices -l
adb -s emulator-5554 shell getprop sys.boot_completed
adb -s emulator-5554 shell getconf PAGE_SIZE
```

## What changed in the spec

The plan now explicitly permits emulator setup and probe-only functional experiments before physical-device access. Overall `probe_status` remains pending because phone measurements have not run. The subsequent runtime experiment is recorded separately.

## Approval recommendation

The recommended standalone runtime experiment has now run; see [its findings and next recommendation](emulator-runtime.md). Complete the physical-device gates before approving production delivery.

## Validation

- Emulator boot/ADB/property checks and Settings UI inspection passed on both guests; measured guest page sizes are 4,096 and 16,384 bytes. KVM access was verified during setup, and the second guest was launched with the granted group credentials.
- Environment snippet and launcher shell syntax passed `bash -n`.
- Delano validation passed through the repository wrapper with zero summary errors/warnings; existing local-only sync observations in other projects remain unchanged.
- Direct Markdown checks include untracked project files. No Android app build, model inference, or physical-phone benchmark was run in this setup step.

## Sources

- [Official Android tools download](https://developer.android.com/studio).
- [Command-line emulator operation](https://developer.android.com/studio/run/emulator-commandline).
- [Hardware acceleration and host/guest architecture requirements](https://developer.android.com/studio/run/emulator-acceleration).
- [Android SDK and AVD environment variables](https://developer.android.com/tools/variables).
