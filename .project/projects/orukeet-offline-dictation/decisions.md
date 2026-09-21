# Decisions

## 2026-09-15: Planned integration recommendations

The user requested an optional downloadable Orukeet add-on. These initial recommendations preceded activation. The later user authorization and implementation decisions below supersede the original hold.

| Decision | Rationale and status |
| --- | --- |
| Treat the model as speech-to-text | Matches the supplied report's transcription flow and the existing keyboard feature. |
| Separate Download and Activate | Required user control; successful download never changes the selected provider. |
| Bundle lazy runtime, download weights | Keeps the large model optional; see review revisions below for the pinned runtime, per-file mirror and ABI packaging. |
| Pin artifact and verify every installed file | Avoids mutable model releases, corrupted transfers, and partial activation. |
| Keep inference in a separate process | Limits native failure impact; minimal application startup is required. |
| Reuse M4A controls with a file-backed local payload | The current byte-returning recorder deletes its source file; descriptor transfer needs explicit ownership/cleanup changes. Compare direct PCM only if measurements justify it. |
| Start with capped completed phrases | Limits memory and avoids streaming reconciliation; proposed cap is 30 seconds, subject to the probe. |
| Reuse local ASR for voice instructions | Existing shared routing makes this coherent; rewrite text can still go to a cloud LLM and must be disclosed. |
| Preserve secure/incognito and Wear boundaries | No policy expansion or watch inference is needed for this first release. |
| Fail closed for local failures | No automatic audio upload or external-IME fallback after selecting local. |
| Require physical-device evidence | Mobile speed, memory, cancellation, and heat are currently unknown. |

Detailed implementation choices, source citations, acceptance gates, and rollback are in [plan.md](plan.md). Revisit proposals from measured results and record changes here. Later entries record implementation activation and remaining release gates.

## 2026-09-15: Review revisions before the probe

These revisions supersede conflicting recommendations in the initial research/update. They remain planned; the user asked for document revisions, not implementation or release publishing. [Review evidence](research/review-revisions/findings.md) records the source checks.

| Decision | Rationale and status |
| --- | --- |
| Measure activation separately and start with filename loading | sherpa's AssetManager branch buffers graphs; its filename branch passes paths to ORT. Allocation overlap remains a risk, but a universal 3x peak is unproven. Keep a provisional 1.5 GiB activation RSS ceiling and report idle/inference memory separately. |
| Name external-data export and direct ORT/TDT contingencies | Test them if the 6 GB candidate fails; either needs new parity/performance evidence and an estimate. Direct ORT alone does not remove embedded-weight parsing. |
| Pin sherpa 1.13.4 / ORT 1.27.0 candidate | Release/build metadata matches export versions. Standard AAR is 48,847,529 bytes; verify binary digest, operators, alignment and per-device cost in Stage A. |
| Download seven files from a prepared Ownkey GitHub Release mirror | Pinned Hugging Face revision is archive-only. Off-device preparation preserves the seven manifest hashes and notices; phone transfer is about 672 MB and first-install free space about 940 MB plus filesystem overhead. No Android bzip2/extraction. |
| Ship per-ABI standalone APKs and retain Play ABI delivery | Avoid universal APK runtime overhead; preserve ordinary typing on other supported ABIs and gate local ASR to validated arm64 devices. Update the release workflow's single-APK assumption. |
| Use a standalone probe before app integration | Proposed `tools/orukeet-probe/` has its own Gradle root; exercise untouched Ownkey under concurrent load. Budget 3-5 probe days and 20-30 total engineering days if the first path passes. |
| Compare isolated and conventional services | Up to half a probe day for model/audio FD loading, sandbox permissions and bounded cancellation; prefer isolation if it works. Same-UID fallback is not a network sandbox. |
| Compare immediate, two-minute and five-minute unload policies | Hide cancels/deletes the utterance immediately; only model state may remain until deadline/pressure. Measure app switching and host survival before selecting the policy. |
| Add `LOCAL` session mode and generalize editor availability | Preserve secure/incognito restrictions while separating local readiness from cloud credentials and wording. |
| Keep Dutch quality gate with corrected evidence | Published Dutch FLEURS WER is 5.60%, but pinned INT8 phone, in-domain, names/numbers and noise results still need measurement. |
| Add AC-013 through AC-016 and define the 5% benchmark | Cover death/OOM, updates, cloud dictation during transfer, and hidden retention. Measure real tap-to-commit and keyboard-open timings. |
| Revise route-specific release copy at Stage E | Preserve the cloud caveat; add verified on-device dictation and local-audio/cloud-text rewrite wording when implemented. |
| Keep probe-first approval order | Fill measured spec findings and go/no-go, approve spec, revise plan, then create executable tasks. Source research does not complete the probe. |

## 2026-09-15: Emulator runtime findings

The user authorized emulator installation and continued the probe by launching the 16 KB guest. The standalone experiment is now complete, with [measured findings](research/emulator-runtime.md) and [aggregate evidence](research/emulator-runtime-results.json).

| Decision | Rationale and status |
| --- | --- |
| Continue physical feasibility with the conventional service | The pinned filename loader transcribed WAV/M4A on the 16 KB x86_64 guest. Stock isolation could read descriptors but could not reopen model paths; Android M4A decode also failed there. Same-UID operation contains native crashes but has no permission-level network sandbox. |
| Treat stronger isolation as additional adapter work | It needs a descriptor-native loader and a compatible audio path, with new memory/lifecycle evidence. Do not loosen file permissions or move models to shared storage. |
| Retain separate activation and inference memory gates | Three successful emulator runs recorded activation high-water RSS of about 848-855 MiB and cumulative high-water RSS through inference of 912-921 MiB. This bounds one loader experiment, not phone memory headroom. |
| Complete cancellation only after observed termination | Native inference is synchronous. The harness observed service death 136 ms after cancel and confirmed PID disappearance; acknowledging cancellation alone is insufficient. |
| Keep physical gates and production state pending | One English fixture, synthetic fault injection and x86_64 timings do not establish WER, arm64 performance, OS OOM behavior, typing responsiveness or retention. |

## 2026-09-15: Implementation authorized

The user requested full implementation after the emulator probe. Proceed with the conventional service, pinned per-file distribution, typed per-session routing, file-backed local audio and explicit model controls. This overrides the earlier hold on implementation, not the numerical release gates. Physical phone evidence remains pending. Release builds must retain a capability gate until a validated device policy is recorded; debug/beta builds allow the experiments needed to obtain it. No app publishing or store submission is authorized by this implementation step.

## 2026-09-15T19:41:00Z: ASR-only native distribution

The stock sherpa-onnx 1.13.4 AAR contains unused TTS/eSpeak code. Its source build enables TTS by default and statically includes the GPL-3.0 eSpeak dependency. No app APK was published. Replace its native payload with the same sherpa revision built with TTS, speaker diarization, C API, executables, websocket and portaudio disabled. Compile Eigen with EIGEN_MPL2_ONLY. Keep the existing ONNX Runtime 1.27.0 binaries and Kotlin bindings; pin the resulting build-time AAR and retain full dependency notices. Publish the reproduction script and source/input pins with that artifact. Repeat native/packaging checks against it; earlier runtime-size and soak results are historical. Model hashes and the published data mirror remain unchanged.

Evidence: [upstream TTS default](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.4/CMakeLists.txt), [pinned eSpeak inclusion](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.4/cmake/espeak-ng-for-piper.cmake), [eSpeak licence](https://github.com/csukuangfj/espeak-ng/blob/ed530aa113046142eb5115cf2fc9157854d0ffe1/COPYING).
