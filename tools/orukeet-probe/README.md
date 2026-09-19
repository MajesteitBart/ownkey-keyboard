# Orukeet Android probe

Disposable standalone Android app for the [Orukeet feasibility plan](../../.project/projects/orukeet-offline-dictation/plan.md). It has its own Gradle root and is excluded from Ownkey's production build.

This original standalone harness retains the stock sherpa-onnx 1.13.4 AAR for historical probe reproduction. Do not distribute that AAR in Ownkey: the application uses the [ASR-only runtime](../orukeet-runtime/README.md) to exclude unused GPL TTS code.

The app loads the pinned INT8 model with sherpa-onnx 1.13.4, transcribes one public English WAV and its Android-encoded M4A equivalent, compares ordinary and isolated inference services, and exercises process death, cancellation and an injected allocation error. It never opens the microphone or an editor. Results contain timings, memory, permission checks, transcript length and a normalized transcript hash; no transcript text is persisted.

## Prepare and build

Use Python 3.11+, `readelf`, Java 21, Android SDK platform/build-tools 36, and a dedicated test guest with enough storage for the approximately 672 MB model. Set `JAVA_HOME`, `ANDROID_HOME` and PATH for the installed tools. On the prepared development host, the shell loads the user-local Android environment automatically.

From the repository root:

```bash
python3 tools/orukeet-probe/prepare.py
./gradlew -p tools/orukeet-probe assembleDebug
```

[pins.json](pins.json) fixes the runtime, model archive/manifest and public fixture URLs, sizes and SHA-256 digests. `prepare.py` verifies them, extracts only the seven manifest payloads **on the host**, checks every native ELF LOAD alignment, and copies the verified AAR into ignored `libs/`. Cache and receipt files default to `$HOME/.cache/ownkey-orukeet-probe`; override with `--cache`. Models, recordings, binaries and raw results stay outside version control. Keep `LICENSE-WEIGHTS` and `NOTICE.md` with the prepared model; these are separate from Ownkey's code license.

This host archive step does not implement the proposed Android downloader. Integrated delivery now uses the published seven-file mirror and its tested resumable downloader; this archive step remains a host-only probe preparation tool.

## Run

Use the running 16 KB guest, or specify another dedicated test device. Close the T3 Device panel with **shutdown disabled** and close its `agent-device` session before using this ADB driver (the helper otherwise retains Android UiAutomation); reopen it after the run. When the panel is attached, use its returned `agent-device` command and target arguments for UI actions instead of direct ADB.

```bash
python3 tools/orukeet-probe/run.py --serial emulator-5556
```

The driver installs the debug APK, provisions verified inputs into the probe's private storage, and runs nine cases. It appends app results without deleting prior runs and writes the current matrix to ignored `evidence/results.json`. Override `--cache`, `--apk` or `--output` as needed. The final normal WAV case checks explicit recovery after failure. The script records observed statuses, including unsupported isolation; completing the driver does not mean every capability passed.

The app also has buttons and accepts this deep link:

```text
ownkey-probe://run?mode=normal&format=wav&failure=cancel
```

Supported values: mode `normal`/`isolated`; format `wav`/`aac`; failure empty/`load`/`inference`/`cancel`/`oom`. Isolated native faults are intentional experiments and can abort that service. Normal and isolated services terminate themselves after each run. The parent observes Binder death; the driver separately checks that the service PID disappeared and the original main PID remains alive. Cancellation is acknowledged first and completed only after process death.

## Interpreting evidence

- WAV input is 7.435 seconds of public 16 kHz mono PCM. The M4A fixture uses Android AAC LC at 64 kbit/s; codec padding can change the decoded sample count. Equal normalized hashes establish fixture parity only, not WER or language quality.
- `load_ms` measures a fresh recognizer in a fresh service process. Host/guest filesystem caches are not flushed; these are not cold-storage or warm-resident p95 measurements.
- Memory values ending in `_kb` are Linux KiB. `activation_hwm_kb` is process VmHWM immediately after loading; it includes earlier startup/audio work. `inference_hwm_kb` is cumulative through inference. A 20 ms RSS sampler reports phase peaks and may miss shorter spikes. PSS is sampled once after loading. Process teardown gives a fresh high-water mark for the next run.
- Inference uses two CPU threads. The 30-second repeated fixture is used only to keep native inference active for kill/cancel experiments. The allocation failure is a deliberately thrown Java `OutOfMemoryError`; it is not observed native OOM or low-memory-killer behavior.
- INTERNET is declared on the app to compare permissions. The isolated UID must report no permission, deny a loopback socket, and lack private-path access. The conventional service inherits INTERNET. A loopback permission check does not establish absence of runtime egress.
- The tested stock path loader cannot reopen passed model descriptors inside the isolated UID; M4A decoding there also fails. See the [recorded findings](../../.project/projects/orukeet-offline-dictation/research/emulator-runtime.md). Do not relax model-file permissions to make this pass.
- This harness does not exercise Ownkey's editor, microphone capture, downloads, audio deletion, selection state or routing. Physical arm64 performance, API 26 compatibility, WER, typing latency, retention and soak gates remain separate work.

The debug APK includes arm64 and x86_64 for experiments. Final per-ABI APK sizes and alignment are recorded in [implementation evidence](../../.project/projects/orukeet-offline-dictation/research/implementation-verification.md); physical installed/Play delivery sizes remain open.

## Integrated app checks

Build `:app:assembleDebug :app:assembleDebugAndroidTest`, close any collaborative Device
panel, and run `python3 tools/orukeet-probe/integration.py --serial <explicit-serial>`.
It verifies the cached public input pins, installs the matching arm64 or x86_64 debug app/test APKs,
provisions the model into app-private no-backup storage, then exercises activation,
transcription, death during inference, cancellation, retry, deactivation and deletion.
It does not record a microphone or retain a transcript. The lifecycle test is opt-in
and skips when the pinned fixture is absent. Use a disposable test installation.

The separate `scheduledDownloadDoesNotActivateOrChangeCloudRoute` instrumentation
method accepts `-e download true`. It launches the settings activity, starts the actual
Wi-Fi/Ethernet Android transfer job, verifies every mirror file, and checks that the cloud
route remains selected and the local runtime remains unloaded. This downloads 672 MB
and deletes any previously installed test model first.

Prepare the immutable model mirror with `prepare-mirror.py --output <staging-dir>`.
The script verifies the pinned source manifest and all seven files, then emits exact
payloads, provenance and checksums. A maintainer publishes a model-data prerelease only
after comparing the upload digests; changed bytes require a new model version.

## Typing and keyboard-open measurements

`benchmark` now includes `TypingLatencyActivity` and `TypingLatencyBenchmark`.
The host contains a synthetic EditText. The test injects actual DOWN/UP touch events
at a measured letter-key centre (`-e keyX … -e keyY …`) and measures elapsed time to
the first host pre-draw after the editor update. It measures keyboard-open time from
`showSoftInput` to a pre-draw with visible IME insets. It excludes ten warm-up taps per
round and writes numeric samples only to the test app's external-files
`typing-latency.json`. No prompts, input text, or transcripts are written.

Build `:benchmark:assembleBenchmark`, install the host test APK and the matching
`:app:assembleBenchmark` ABI APK. Invoke the benchmark runner with class
`dev.patrickgold.florisboard.benchmark.TypingLatencyBenchmark`, the measured `keyX/keyY`,
`rounds=20`, `taps=100`, and `opens=10`. The default target is the `.bench` app; `targetPackage`
can name another installed Ownkey test build. Before warm measurements, launch the
Ownkey settings app, let extension initialization finish, and verify the selected
keyboard renders and accepts a letter. Do not include first installation/startup in
these warm samples. An emulator smoke immediately after APK replacement exposed an
empty-layout initialization race: the combined extension list lagged behind keyboard
indexing. Extension lookup now uses the current source indexes directly. Track cold
IME startup separately, including this failure mode. Fix the layout, subtype, orientation,
font scale, refresh rate, keyboard height and suggestion settings across both builds.
A run without key coordinates deliberately skips instead of fabricating a measurement.

On each physical tier, alternate baseline/candidate runs in balanced order. Compare
at least 20 matched rounds with `compare-latency.py baseline.json candidate.json`.
It reports percentage changes in pooled typing p50/p95 and keyboard-open p95,
bootstrapping paired whole rounds for a 95% confidence interval. Each round discards
one warm-up opening and ten warm-up taps. Schema 2 records opening arrays per round;
the comparator rejects fewer than 20 rounds, 100 taps or 10 openings per round.
Repeated openings measure warm reopen; collect cold-start traces separately on phones. Pass requires the
upper bound ≤5%; an interval crossing 5% is inconclusive. Run baseline against itself
to measure noise first; do not widen the gate to hide noise. Repeat on idle, absent,
downloaded/inactive, download, hash verification, activation, inference, and hidden
resident conditions. Coordinate each condition with the UI or test controller and
record which phase covered each round; discard rounds that did not overlap their
intended phase. Emulator timings are harness checks, not the phone regression gate.

## Accuracy evidence

`score.py` reads local JSONL records with `id`, `language` (`nl`/`en`), `subset`,
`reference`, and `hypothesis`. It emits aggregate edit/word counts, WER and silence false
positives by language and subset, never raw text. Normalization is NFKC, casefold,
punctuation-to-spaces; it does not expand numbers or normalize names. Collect ≥50
consented/public phrases per language, including the specified subsets. Keep corpus
licence/consent and audio hashes in a private local manifest; only safe aggregate
results enter the project. A single English fixture does not establish Dutch quality.

Run evidence-tool checks with `python3 -m unittest discover -s tools/orukeet-probe -p 'test_*.py'`.
Use `check-packages.py --zipalign <SDK-zipalign> <ABI-APKs…>` for native/ZIP alignment,
ABI inventory, absence of ONNX payloads, sizes and checksums.
