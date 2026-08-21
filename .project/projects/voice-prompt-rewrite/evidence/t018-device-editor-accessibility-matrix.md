# T-018 Device, Editor, and Accessibility Matrix

Date: 2026-08-05
Gate decision: blocked

## Environment

- Emulator: `TwentyGoApi35`, Android 15 / API 35, `sdk_gphone64_x86_64`.
- Phone profile: 1080 x 2400 at 420 dpi (411 dp width), portrait; font scale 1.3.
- Expanded profile: 1600 x 2560 at 320 dpi (800 dp width), portrait with split keyboard.
- Chrome: 124.0.6367.219.
- Google Messages: `messages.android_20240528_00_RC01_alldpi.x86_64.phone`.
- TalkBack: 15.0.0.639625893 with touch exploration enabled during the accessibility pass.
- App: freshly assembled and installed debug APK; microphone permission granted. No provider secrets were
  configured or recorded.

## Results

| Area | Result | Evidence / notes |
| --- | --- | --- |
| Chrome omnibox Select All | Pass | `t018-browser-select-all-provider-recovery.png`: no initial selection becomes a visible 19-character whole-field selection before the missing rewrite-provider recovery appears. No recorder/provider work starts before the recovery. |
| Compose secure field | Pass after remediation | The AI API-key field initially exposed `inputType=0x8001`, so the hub was incorrectly available. T-016 was reopened and both API-key fields now use password keyboard options. The emulator reports `inputType=0x8081`; `t018-compose-secure-gate-fixed.png` shows the mic, voice card, and presets disabled with `AI is off in secure fields`. |
| Expanded/tablet split layout | Pass for available states | `t018-tablet-split-hub.png`: at 800 dp width the voice card spans the bounded content group, the preset grid remains two-column, and controls are not stranded at the display edges. This also exercises the 1.3 font scale. |
| TalkBack service and focus | Partial | TalkBack and touch exploration were active. `t018-talkback-secure-gate.png` shows accessibility focus on the secure Compose field while the IME exposes the disabled secure-field hub. Full spoken entry/recording/review/recovery traversal was not possible without configured test providers; initial emulator TTS setup also emitted a not-ready warning. |
| Phone/landscape dark rendering | Pass from current RC | Existing T-016 evidence covers compact portrait, short landscape/split, 1.3 font scale, selected-target hub, recovery, recording, and paused states on this API 35 AVD. |
| Simulated editor/lifecycle matrix | Pass, not physical evidence | The 273-test JVM suite includes native/Compose/messaging/WebView/raw/secure/problematic editor profiles, selection retention and drift, five lifecycle invalidations, gesture timing, reduced motion, language hints, and stale-target blocking. These tests remain deterministic support evidence, not a substitute for the missing representative physical-device pass. |
| App stability during matrix | Pass | No Ownkey `FATAL EXCEPTION` / process crash was present in the post-matrix logcat scan. |

## Defect found and resolved

The visual password transformation in the Compose API-key fields did not advertise a password
keyboard type to the IME. That made the fields look masked to the user while the shared cloud-AI
availability policy correctly saw only an ordinary editor. T-016 was reopened through Delano, both
API-key fields were changed to `KeyboardType.Password`, and the debug APK was rebuilt and reinstalled.
The resulting Android editor contract and visible gate now agree.

Verification after remediation:

- `:app:compileDebugKotlin :app:assembleDebug` passed.
- `:app:compileReleaseKotlin` passed after the remediation.
- `:app:testDebugUnitTest` passed 273 tests across 39 suites with 0 failures and 0 skips.
- `git diff --check` passed.

## Release-blocking gaps

T-018 cannot honestly close on this environment. The following required evidence is still missing:

- a representative physical phone/tablet and real microphone for silence, quiet speech, ordinary
  speech, pause, reduced motion, saturation, and host-configured touch-and-hold timing;
- a complete TalkBack traversal through disclosure, recording controls, processing, review,
  replacement/copy, and every recovery path with working TTS;
- configured consented test providers for the source-language/explicit-translation matrix and the
  hinted-dictation versus unhinted short-instruction comparison;
- rendered light and additional custom keyboard themes; and
- fresh physical/editor execution across native, Compose, messaging, browser/WebView/contenteditable,
  and raw/problematic editors rather than relying on the M0 emulator plus deterministic simulations.

Until those checks pass, T-019 and any closed/beta or production rollout remain blocked. No real
typed/spoken/selected content, credentials, provider bodies, raw prompts, endpoint details, or
machine-specific paths are included in this evidence.
