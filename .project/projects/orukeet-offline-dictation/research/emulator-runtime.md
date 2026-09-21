# Emulator runtime probe

Date: 2026-09-15
Status: Narrow functional probe complete; physical feasibility and production approval remain pending.

## Uncertainty tested

Can the pinned Android runtime load Orukeet and transcribe a bounded public fixture on a 16 KB guest? Do model/audio descriptors work inside an isolated service, and can the parent survive native failure and cancel active inference?

## Experiment and provenance

The [standalone harness](../../../../tools/orukeet-probe/README.md) is a separate Gradle root, excluded from the production app. This session bounded the experiment to one API 36 x86_64 guest, two inference threads, one public English recording, two audio formats and nine cases. No microphone or editor content was collected.

The guest was `ownkey-api36-16k`: Android 16/API 36, x86_64, actual page size 16,384 bytes, configured 6 GiB RAM and two vCPUs, using host KVM. [Setup evidence](emulator-setup.md) describes the environment and PATH repair. The ordinary 4 KB guest was boot-verified earlier but was stopped during this experiment; no runtime result is claimed for it.

[Aggregate results and provenance](emulator-runtime-results.json) include all nine runs, source/APK digests, complete input hashes, native-library sizes/alignment and guest fingerprint. No raw transcript, audio, binary, machine-specific host path or secret is included. The model and public fixture stay in ignored local storage. Reproduction commands are in the harness README.

Verified inputs:

- sherpa-onnx 1.13.4 AAR: 48,847,529 bytes; SHA-256 `03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780`. Its Android build targets ORT 1.27.0 as established in [source verification](review-revisions/findings.md); the probe did not independently query the runtime version string.
- Orukeet revision `55a984d46f68323301837194ce647c702f55facc`: archive and all seven extracted files matched the pinned manifest. Archive SHA-256 `f9191f30178cc9122ce2f023bf9fefafc822028307b0efa4caff645ba3fe8d0a`. Host extraction does not change the planned seven-file phone download.
- Public fixture: pinned upstream Parakeet sample, 7.435 seconds, mono 16 kHz PCM16; SHA-256 `5fceacff0315d49cb59fcc505bcecf1ed5f2f35c2897b1e65a59f30e5d922150`. Android encoded an AAC LC M4A at 64 kbit/s for the second input path.

## Observed results

| Case | Result |
| --- | --- |
| Conventional process, WAV | Loaded and transcribed; 122 transcript characters. |
| Conventional process, M4A | Decoded from a read-only descriptor and transcribed; normalized transcript hash matched WAV. AAC padding produced 121,856 samples versus 118,960 WAV samples. |
| Isolated process, WAV | Descriptor reads and PCM decoding worked. Reopening model descriptors through `/proc/self/fd` failed; ORT aborted with error 13 (permission denied). Parent observed service death during load. |
| Isolated process, M4A | Android audio decoding raised `IOException` before model loading. Exact media-service failure needs a separate investigation if isolation is pursued. |
| Kill conventional service during load | Parent observed death; no result content returned; main PID survived. |
| Kill conventional service during native inference | Parent observed death; no result content returned; main PID survived. |
| Cancel conventional native inference | Cancellation acknowledgement in 1 ms; Binder death in 136 ms. Driver confirmed service PID absent afterward. |
| Inject allocation failure in isolated service | Deliberately thrown `OutOfMemoryError` reported; service terminated; main PID survived. This is not OS OOM evidence. |
| Explicit conventional WAV retry after failures | Successful transcription with the same hash and the same surviving main PID. |

Every completed case ended with the service PID absent and the original main PID alive. The final successful result also rendered in the T3 Device panel. This proves the harness's process boundary and explicit retry path, not Ownkey editor/session safety or typing responsiveness.

### Memory and timing

The three successful runs in the final matrix measured:

| Run | Model load | Native inference | Activation RSS high-water | RSS high-water through inference |
| --- | --- | --- | --- | --- |
| WAV | 1,428 ms | 1,274 ms | 848.0 MiB | 911.6 MiB |
| M4A | 1,204 ms | 1,408 ms | 855.3 MiB | 920.7 MiB |
| WAV after failures | 1,248 ms | 1,256 ms | 847.7 MiB | 911.5 MiB |

Post-load PSS was about 724-725 MiB. Fresh processes used `OfflineRecognizer(assetManager = null)` and the filename loader. The kernel high-water marks include startup and audio preprocessing; the accompanying 20 ms RSS sampler can miss shorter spikes. Post-load PSS was sampled once. Each recognizer was released and its process terminated; no idle retention or warm-resident run was measured. Host/guest filesystem caches were not flushed.

These results are below the provisional 1.5 GiB RSS ceiling on this guest. They do not establish phone memory headroom, cold-storage p95, a supported RAM tier, sustained performance or thermal behavior. They also do not support treating a 3x encoder allocation as an unavoidable lower bound for this loader/artifact.

### Isolation and packaging

Isolated services received distinct UIDs, had no INTERNET permission, could not read original private paths, and were denied a loopback socket. Their passed file descriptors remained readable. The stock runtime needs to reopen paths, which failed under the isolated UID. The conventional process inherited the app's INTERNET permission and could open a socket; no claim of permission-enforced network isolation or complete egress testing applies to it.

All 16 native libraries across the AAR's four ABIs had ELF LOAD alignment of 16,384 bytes. The two-ABI probe debug APK passed `zipalign -c -P 16 4` and installed/loaded native code on the 16 KB x86_64 guest. It was 67,738,260 bytes, including 31,246,808 bytes of arm64 libraries and 35,435,512 bytes of x86_64 libraries, stored uncompressed. This is probe packaging, not production download overhead. Arm64 16 KB execution, ABI-specific release APKs, Play-delivered size and API 26 remain untested.

## Changes to the spec and plan

- Continue the next feasibility measurements with the conventional unexported inference process and filename loading. The stock isolated FD-path approach has failed this experiment.
- Keep isolation as a separate contingency needing a loader that consumes descriptors directly, plus an audio-decoding solution. Reopening `/proc/self/fd` is not sufficient; do not widen file permissions or copy private models to shared storage. Reassess allocation overlap if a custom loader buffers weights. No custom JNI/decoder build was attempted in this time box.
- Keep activation and inference peaks separate. The observed result reduces one uncertainty but leaves the physical gates intact.
- Preserve the existing external-data and direct ORT/Kotlin TDT contingencies if phone memory gates fail. Neither automatically solves sandbox descriptor or media access.

## Touched surfaces and footguns

- Added only the standalone harness and project evidence/plan updates. The production app, root Gradle inclusion and executable production task state remain unchanged.
- Runtime native exceptions can abort the service before a Kotlin catch executes. The harness sends aggregate evidence before loading so the parent retains failure context.
- Descriptor readability does not imply a second `open()` on its proc path is permitted. M4A media services need their own isolated-process test.
- Native inference is synchronous. Cancellation must invalidate results and terminate/recreate the service, or use a separately verified runtime interruption path. An acknowledgement alone does not prove work stopped.
- The app retains only the synthetic public fixture and aggregate results. Production file cleanup, no-backup placement, editor invalidation and main-process crash recovery still require implementation and tests.

## Remaining work and recommendation

**Run another narrow probe.** Continue on physical arm64 devices with the conventional service, then add the planned typing benchmark, in-domain Dutch/English corpus, thread comparison, constrained-memory/OOM observation, cold/warm metrics, 15-minute soak and retention/trim experiments. Download/update and cloud-concurrency acceptance cases also remain unimplemented. The emulator experiment does not approve the spec or activate production tasks.

## Validation

- Standalone debug build passed; pinned inputs and all native ELF alignments verified; APK 16 KB ZIP alignment passed.
- Nine-case emulator matrix completed with the successes and expected/observed failures above; aggregate evidence preserved. Runtime results were not rerun after documentation-only changes.
- Project contract/link/whitespace validation is recorded in the corresponding [progress update](../updates/2026-09-15-emulator-runtime.md).
