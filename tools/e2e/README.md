# End-to-end tests

Maestro flows that drive the debug build on an Android emulator: the settings app and the keyboard itself. `run.sh` prepares the device with adb, runs each flow on its own, and writes a JUnit report.

## Requirements

- Maestro 2.5.1 on `PATH`, or `MAESTRO=<path to maestro>`.
- `adb` and a running emulator. The flows were written on a Pixel 6 AVD (1080x2400, 420 dpi, API 35, `google_apis` x86_64).
- A debug APK: `./gradlew :app:assembleDebug`. The default is `app/build/outputs/apk/debug/app-x86_64-debug.apk`.
- Works from Git Bash on Windows. The script sets `MSYS_NO_PATHCONV=1` itself.

## Run

```bash
tools/e2e/run.sh                                   # install, prepare, run every flow
tools/e2e/run.sh --flows 02,20 --no-install        # some flows, by number or full name
tools/e2e/run.sh --repeat 2                        # every flow twice, to catch flakiness
tools/e2e/run.sh --serial emulator-5556 --out tmp/e2e-other
tools/e2e/run.sh --flows 20,21 --run 2 --no-install   # label the results as run 2 (split a pass into batches)
tools/e2e/run.sh deeplink settings/privacy         # open one settings deep link and exit
tools/e2e/run.sh restore-onboarding                # mark setup done again after an interrupted 00-setup
```

| Option | Default | Meaning |
|---|---|---|
| `--serial` | `emulator-5554` | adb serial of the device |
| `--apk` | `app/build/outputs/apk/debug/app-x86_64-debug.apk` | installed with `adb install -r -g` |
| `--flows` | `all` | comma-separated flow numbers or names |
| `--out` | `tmp/e2e-out` | output folder (keep it under `tmp/`, which git ignores) |
| `--repeat` | `1` | run the selection N times |
| `--run` | `1` | number of the first run; results go to `run<N>/` and `junit/<flow>-run<N>.xml` |
| `--reset` | off | `pm clear` before the onboarding flow (see below) |
| `--no-install` | off | skip the install |

The script exits non-zero when a flow fails or the app crashes during a flow.

Output in `--out`:

- `report.xml`: every JUnit file in `junit/` merged into one, including earlier invocations with other `--run` numbers. Delete `junit/` to start a fresh report.
- `junit/<flow>-run<N>.xml`: one JUnit file per flow run.
- `run<N>/<flow>/screenshots/*.png`: the screenshot each flow takes at its key moment.
- `run<N>/<flow>/maestro.log`, `debug/`, and a failure screenshot and command log when a step fails.
- `run<N>/<flow>/crash.txt`: the crash buffer, only when the app crashed.

## What the runner does

Before the first flow it grants `RECORD_AUDIO` and `POST_NOTIFICATIONS`, enables and selects the Ownkey keyboard, turns off animations, sets `cmd uimode night yes`, keeps the screen on and shows the keyboard even with a hardware keyboard attached.

Before every flow it force-stops the app, so the flow starts on Home with an empty preview field, and selects Ownkey again. Android switches to another keyboard whenever the keyboard's package is force-stopped, which is also why flows never use `launchApp: stopApp: true`.

Before `00-setup` it resets onboarding without wiping the app: it clears the setup flag and the stored permission answers in the preferences file (through `run-as`, so it needs the debuggable build) and revokes the two permissions. If the flow fails half-way, the runner marks setup done again so later flows don't land on Setup (`run.sh restore-onboarding` does the same by hand). `--reset` uses `pm clear` instead. That also deletes the downloaded on-device model, saved API keys and added languages, so use it only on a throwaway device.

`30-deeplinks` isn't a YAML flow. The runner opens each settings deep link with `CATEGORY_BROWSABLE` and an explicit component, then checks the screen with `maestro/helpers/assert-id.yaml` or `assert-text.yaml`. Without the BROWSABLE category the app treats a `ui://` link as an extension import.

Only one UiAutomation client can be connected at a time. If agent-device's snapshot helper is running, the runner stops it so the Maestro driver can start.

## Coverage

| Flow | What it checks |
|---|---|
| `00-setup` | Setup opens on the microphone step, accepts the microphone and notification prompts, finishes on Home, and doesn't come back on the next launch |
| `01-home-navigation` | Every Home row opens its screen; system Back and the top-bar Back return |
| `02-appearance` | Dark, Light and Auto; the Graphite and Black tiles; accent circles; choices survive leaving and relaunching; preview keyboard in Light and Dark |
| `03-keyboard-settings` | Number row on and off with persistence, Number hints disabled while it's on, the key next to space sheet, Sound & vibration, a pick in the Gestures sheet |
| `04-typing` | Autocorrect level sheet with persistence, the Double-space for period switch with persistence, Saved words |
| `05-languages` | The list shows English; Add language opens, a search for "Dutch" finds the Dutch preset and the "Set up another language" row, and Back adds nothing |
| `06-clipboard-emoji` | Delete old items reveals its slider and persists, the skin tone sheet, the Clear emoji history confirm (cancelled) |
| `07-privacy` | Incognito sheet with persistence, the Clear learned data confirm (cancelled), Back up and Restore screens open |
| `08-about` | Seven taps on the version turn on developer tools; the Devtools switch turns them off again; the license screens open |
| `09-ai` | AI sections, Cloud disabled without a key and opening the key dialog, a fake key typed and cancelled, the rewrite provider sheet, adding, editing and deleting a rewrite style |
| `10-personal-dictionary` | Add a word, see it after leaving and coming back, delete it |
| `20-keyboard-typing` | Types "hello world" in the preview field with key taps |
| `21-toolbar` | Expands and collapses the toolbar, opens and closes the overflow |
| `22-voice-only` | Overflow, Voice only, the bar replaces the keys, Show keyboard brings them back |
| `23-rewrite-no-key` | Select all, open the rewrite panel, tap a style and get the "needs an API key" message, close the panel |
| `24-emoji` | Opens the emoji panel, searches "smile" with the keyboard's keys, goes back to letters |
| `30-deeplinks` | 19 settings deep links, including the old Smartbar, Media and Other aliases |

Every flow puts back what it changes, so the suite can run repeatedly on the same device. The toolbar is left collapsed, its default.

## Safety

- No real API keys. `09-ai` types `fake-e2e-key-not-real` and taps Cancel; it never taps Save.
- `23-rewrite-no-key` first checks that Home reports no rewrite key, and never taps the voice card, so nothing is sent anywhere.
- No flow taps a microphone button, so nothing is recorded.
- No flow downloads, deletes or activates the on-device model.

## Selectors and device assumptions

- Settings screens use Compose test tags. `testTagsAsResourceId` is on for the settings root, dialogs, sheets and the keyboard, so tags work as Maestro `id:` selectors.
- Keyboard buttons and toolbar actions use their content descriptions ("Show actions", "More actions", "Voice only", "Close AI rewrite").
- Letter keys are matched by their labels, below the mic (or the emoji search bar) so suggestion chips never match (`helpers/type-word.yaml`).
- The space bar has no label of its own. `20-keyboard-typing` finds it as the text between the `,` and `.` keys on the bottom row, which is the language name the space bar shows. Without a space bar label, that step would need a point tap. On the 1080x2400 AVD the space bar is at `50%,91%`.
- Maestro's `launchApp` grants every runtime permission unless the flow says otherwise. `00-setup` passes `permissions: {all: unset}` so Setup has to ask.
- Compose reports a selected choice row (radio button role) as `checked`; only tabs, such as the Dark/Light/Auto segments, report `selected`.
- Maestro 2.5.1 never matches `containsChild` combined with `checked: false` or `enabled: true`, even when the row is in that state. The flows check those states as "not visible with `checked: true`" or "not visible with `enabled: false`" instead. `id:` combined with `checked: false` works.
