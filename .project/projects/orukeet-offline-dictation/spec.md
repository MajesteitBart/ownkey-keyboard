---
name: Optional Orukeet offline dictation
slug: orukeet-offline-dictation
owner: ownkey-keyboard-team
status: active
created: 2026-09-15T10:30:28Z
updated: 2026-09-15T22:34:27Z
outcome: Users on validated Android phones can explicitly download and activate Orukeet, dictate short phrases offline, and remove the model, with no more than 5% regression in typing and keyboard-open p95 latency.
uncertainty: high
probe_required: true
probe_status: pending
---

# Spec: Optional Orukeet offline dictation

## Executive Summary

Add **Orukeet (on device)** under Settings -> AI -> Dictation. Orukeet is speech-to-text (ASR), which matches keyboard dictation. Downloading its model does not activate it. Existing users retain their current route until they explicitly choose Orukeet.

The first release handles bounded recordings followed by completed transcripts. Reuse the mic, pause/resume, cancel, insertion, and voice-rewrite review flows. Local dictation requires microphone permission and verified model files, but no API key or network. Voice rewrite can transcribe the instruction locally and then send instruction text and selected text to the configured cloud LLM.

The user authorized full implementation after the emulator probe. The integrated app now includes model lifecycle controls, pinned downloads, session snapshots and conventional-process inference. The isolated path failed model reopening and M4A decoding. Physical acceptance remains pending and public enablement stays gated. See [implementation verification](research/implementation-verification.md), [initial probe findings](research/emulator-runtime.md) and [the delivery plan](plan.md).

## Problem and Users

Users wanting offline Dutch and English input need a local option without adding hundreds of megabytes to every installation. Large native models must not impair everyday typing or keyboard reliability.

## Outcome and Success Metrics

These are proposed acceptance targets, not measured Orukeet Android performance:

- Zero model bytes downloaded without a Download action; zero automatic activations.
- All local-dictation acceptance cases pass with networking blocked, including after app/device restart.
- Ordinary typing and keyboard-open p95 latency regress by no more than 5% on the same device, including during model download, verification hashing, activation, inference, and hidden model retention. Measure tap-to-host-commit and request-to-visible-keyboard separately using the plan's paired-run protocol; app startup is not a substitute.
- On the validated device tier, warm stop-to-insert p95 <=3 seconds for 5-second phrases and <=5 seconds for 10-second phrases; cold model-ready p95 <=10 seconds.
- Cancellation, editor changes, and local failures never insert stale text or send audio to a fallback provider.
- Activation/first-inference peak memory and idle/warm-inference memory each meet their own recorded gate. The initial service ceiling is 1.5 GiB, with high-water RSS for activation and RSS/PSS reporting throughout; no OOM, ANR, or host/IME loss on supported devices. Device evidence must set the final resident budget and supported tier.
- Clean in-domain Dutch and English WER each <=10%, with names, numeric values, noise, and mixed-language subsets reported separately. Reference-checkpoint read-speech scores do not establish Android INT8 quality.
- At least 9 of 10 first-time test users can download, activate, dictate, deactivate, and find Delete model without coaching.

## User Stories

- US-001: Download and activate local dictation once, then speak without a connection or API key.
- US-002: Preserve cloud configuration while trying and later removing Orukeet.
- US-003: See whether an action processes audio locally or sends text to a rewrite provider.

## Acceptance Scenarios

- AC-001: Fresh installs/upgrades preserve current routing. Opening the keyboard or settings neither fetches nor loads a model.
- AC-002: Download shows approximately 672 MB payload, calculated free-space requirement, compatibility, attribution, and network choice. Seven per-file transfers resume safely or restart using pinned sizes/hashes; partial files never become selectable. First-install space is approximately 940 MB with the proposed 256 MiB reserve, plus small filesystem overhead.
- AC-003: After verified installation, the card says Downloaded and offers Activate. The previous route stays selected until activation's local compatibility/load check succeeds.
- AC-004: With Orukeet selected, networking blocked, and no cloud key, record -> stop -> insert works, including after restart.
- AC-005: Local errors offer retry, model management, or an explicit provider change for a new recording. No cloud or external-IME fallback occurs automatically.
- AC-006: Deactivation retains files and explicitly restores the prior route. Deleting an active model confirms the route change, cancels work, releases resources, and removes files.
- AC-007: Cancel, keyboard hide, editor switch, secure transition, or IME teardown invalidates recording/inference; late callbacks cannot modify host text.
- AC-008: Voice rewrite transcribes its instruction locally, discloses cloud text processing, and preserves preview/Replace and target validation.
- AC-009: Existing secure-field and incognito restrictions remain in v1. Local integration does not bypass these gates.
- AC-010: Restoring settings without model files shows Download required. It neither trusts a restored installation flag nor automatically downloads or changes providers.
- AC-011: Unsupported ABI, insufficient storage, failed checksum, or incompatible runtime prevents activation with a specific explanation; typing stays usable.
- AC-012: At the displayed local recording cap, capture stops once and processes the bounded phrase; audio is not silently truncated.
- AC-013: Inference process death, allocation failure/OOM, or timeout during activation/transcription leaves the keyboard responsive and the local selection fail-closed. Invalidate the session, insert no partial/stale text, close audio descriptors, remove temporary audio, and offer explicit retry. Recovery never uploads audio, switches provider, or reloads in a crash loop. Distinguish injected failures from observed OS OOM evidence.
- AC-014: A user-requested model update downloads and verifies a candidate while the existing verified version remains usable. A current session keeps its version lease. Switch only after sessions end, unload the old recognizer, and load-test the candidate; never hold two recognizers. Failure or process/restart interruption leaves the prior verified version available for local retry. No update downloads or activations occur without the user's update action.
- AC-015: With cloud dictation selected, start/pause/resume/stop/transcribe/insert and cancel still work during model transfer and hashing, including transfer retry/cancel and network contention. Download success does not change provider. Cloud request failures remain recoverable under the existing cloud flow; model transfer must not hold the microphone lease or block it.
- AC-016: Hiding the keyboard cancels the utterance and deletes its audio immediately. If the measured policy retains the recognizer for two to five minutes, it retains model state only. Reopen within/after the idle deadline, memory-pressure signals, and background service death behave predictably; expired/dead models load only on explicit local voice use. No old transcript can be inserted after reopen.

## Scope

### In Scope

Android phone/tablet support beginning with validated arm64 devices; model download/install/activation/deactivation/deletion/update; ordinary dictation and local instruction transcription for voice rewrite; readiness, migration, disclosures, resource limits, editor safety, and backup behavior.

### Out of Scope

Text-to-speech; default installation; hosted ASR; background listening; streaming captions/chunk reconciliation; diarization; imported recordings and long meetings; Wear OS inference; local LLM rewriting; arbitrary model imports; fine-tuning; GPU/NPU acceleration; relaxing incognito restrictions; Play publishing during planning.

## Functional Requirements

- Separate installation state, selected backend, and temporary runtime state.
- Store versioned verified files outside cache and backup paths; expose per-file/total progress, cancel/retry, sizes, version, and notices. Use a pinned Ownkey GitHub Release mirror; prepare the seven verified files off-device because the pinned upstream revision only exposes an archive.
- Snapshot backend, model version, and audio configuration per session. A settings change cannot reroute recorded audio.
- Add a local audio session mode and backend-neutral editor availability policy. Preserve active-editor, secure-field, and incognito restrictions; separate local-model readiness from cloud ASR/LLM credentials and use route-appropriate errors.
- Local M4A capture produces an owned file-backed payload under `noBackupFilesDir/ai-audio/`; the main session passes a read-only descriptor to inference and owns terminal/orphan cleanup. Adapt the current recorder, which returns bytes and deletes its cache file. Cloud/mock payload behavior remains supported.
- Keep one microphone session and one local inference operation at a time.
- Retain cloud settings/keys; hide cloud-only language hints for local recognition and describe automatic multilingual recognition honestly.
- Keep phone model/selection separate from existing Wear configuration sync.

## Non-Functional Requirements

Keep per-file download, hashing, loading, decoding, and inference off typing-critical threads; no Android archive extraction. Contain native failures in a dedicated process with minimal application startup. Use the conventional same-UID service for the next probe following the isolated loader/media failures. It contains native crashes without a permission-level network sandbox. Any future isolated-service design needs a separately verified descriptor-native loader and audio path. Bound recording duration, activation and resident memory, CPU threads, queued work, and idle retention using device evidence. Retain no audio/transcript history or content-bearing logs. Ship runtime code through app releases and per-ABI standalone APKs; download only model data and notices. Preserve model and dependency attribution separately from Ownkey's code license.

## Assumptions

Bundling a lazy-loaded runtime while downloading the large model separately meets the add-on requirement. The initial stock sherpa-onnx 1.13.4 AAR was 48.8 MB and targeted ORT 1.27.0, matching the export environment. A packaging audit found unused GPL TTS dependencies, so the integrated app uses the same sherpa source rebuilt without TTS, with Eigen MPL2-only and the original ORT/bindings. The pinned ASR-only AAR is 19,696,942 bytes and contains arm64/x86_64 only; native 16 KB alignment is verified. Final package evidence is recorded separately; physical arm64 execution remains pending. Upstream ONNX compatibility is a candidate path, not proof of mobile performance. The plan budgets 3-5 days for the standalone probe and 20-30 engineering days including it if the first artifact/runtime path passes.

## Needs Clarification

No unanswered user preference prevents this plan. The probe must establish the supported device tier, runtime build, recording cap, and final memory/latency budgets before public enablement. Ownership provisionally follows the existing Ownkey team.

## Hypotheses and Unknowns

The emulator established graph loading, single-fixture transcription, conventional process cancellation/retry and x86_64 16 KB packaging. The stock isolated FD-path loader and M4A decode failed. Phone activation allocation overlap, idle residency while hidden, warm inference, cold loading, sustained performance, in-domain Dutch accuracy, phone cancellation and the physical arm64/API matrix remain unmeasured. A 2-3x encoder allocation estimate is a stress hypothesis, not a proven lower bound. If the filename loader fails memory gates, evaluate an external-data re-export and direct ORT Android with Kotlin TDT decoding as separate contingencies before revising scope.

## Touchpoints to Exercise

AI settings, mic/recording row, voice-rewrite disclosure, editor invalidation, app/service startup, model storage/downloads, backup/restore, upgrade migration, standalone APK and Play packaging.

## Probe Findings

Emulator setup is recorded in [setup evidence](research/emulator-setup.md). The subsequent [runtime probe](research/emulator-runtime.md) and [aggregate results](research/emulator-runtime-results.json) record nine cases on the 16 KB x86_64 guest. WAV/M4A transcription matched for one public fixture; activation high-water RSS was 848-855 MiB and high-water RSS through inference was 912-921 MiB across three successful runs. Conventional cancellation reached observed service death in 136 ms, and explicit retry succeeded with the same main PID. Isolated descriptor reads worked, but model path reopening and M4A decoding failed. These are emulator observations, not arm64 phone acceptance results.

The [integrated implementation evidence](research/implementation-verification.md) records the final ASR-only runtime, memory phases, offline lifecycle, and sustained run. The earlier stock-runtime measurements above are historical.

The required physical-device probe remains pending. No device pass, supported tier, or production readiness is claimed. [Original research](research/android-integration/findings.md) and [review verification](research/review-revisions/findings.md) establish these pre-probe facts:

- sherpa-onnx 1.13.4 supports filename loading without the AssetManager whole-file buffer; embedded ONNX weights still require parsing/runtime allocations.
- Runtime release/version/size metadata is available. The pinned seven-file manifest is valid, but the upstream revision exposes only the archive, so a mirror is a delivery dependency.
- The recorder's byte-array/cache behavior, cloud-named editor policy, and missing typing benchmark require explicit work.
- Upstream reports Dutch FLEURS WER of 5.60% for 364 recordings. Ownkey still needs pinned INT8 phone and in-domain quality evidence.

Before public enablement, complete the following findings from the standalone `tools/orukeet-probe/` harness and revise the plan. Partial emulator evidence does not waive the remaining gates.

| Required finding | Evidence/result |
| --- | --- |
| Device/OS/ABI, artifact/AAR hashes, ORT version, loader path | API 36 x86_64/16 KB, pinned inputs and filename loader verified; ORT 1.27.0 from build provenance. Physical arm64/API matrix pending. |
| Activation/first-inference high-water RSS and sampled PSS; idle/warm memory; system headroom | Emulator high-water RSS and post-load PSS recorded. Phone peaks, sampled PSS, idle/warm memory and system headroom pending. |
| Cold/warm latency, 1/2/4-thread comparison, recording cap and 15-minute soak | Pending physical-device probe |
| Dutch/English WER, names/numbers/noise subsets, reference-artifact parity | Pending physical-device probe |
| Typing and cold/warm keyboard-open paired-run p95 regression | Pending physical-device probe |
| Isolated service FD loading, permissions and cancellation/death outcome | Isolated UID/no-INTERNET confirmed; stock model reopening and M4A failed. Conventional kill/cancel/retry passed in the harness; integrated checks pass; phone checks pending. |
| Immediate/two-minute/five-minute retention, trim and background process death | Pending physical-device probe |
| Package overhead, supported tier, failed gates and contingency decision | All 16 ELF libraries and probe APK pass 16 KB alignment; x86_64 loads. Continue conventional service after isolation failure. ASR-only release APK sizes/alignment are recorded; arm64 execution and supported tier pending. |
| Go/no-go and spec approval basis | Implementation authorized explicitly; public enablement remains blocked on phone/corpus gates. |

## Footguns Discovered

- Existing capture returns compressed M4A bytes and deletes the source cache file; descriptor transfer needs a file-backed contract, ownership and cleanup, plus actual waveform decoding.
- Current routing depends on API-key presence and includes debug mocks/external-IME fallback.
- Audio session mode has no local value. The cloud-named policy enforces editor privacy restrictions; renaming must preserve those restrictions.
- Application startup clears cache and initializes keyboard services; a second process must not run this path unchanged.
- Coroutine cancellation alone does not prove synchronous native inference stopped.
- A same-UID service has INTERNET permission through the app. In the isolated emulator process, descriptor reads worked but reopening `/proc/self/fd` failed with permission denied and aborted the native loader. M4A decoding also failed before model loading; isolation needs additional adapter work.
- Loading from paths avoids one buffer, not all ONNX allocations. An idle/post-inference memory sample can miss the activation peak.

## Remaining Unknowns

See the plan's feasibility gate and delivery risks. Emulator timings are recorded; no phone speed, minimum RAM, production completion or accuracy gate is claimed.

## Dependencies

Existing voice-prompt-rewrite audio/session and editor-safety contracts; a verified sherpa-onnx Android build; pinned model distribution; physical arm64 devices; normal Android release checks.

## Approval Notes

The user explicitly requested full implementation after the emulator findings. Implementation is authorized with the conventional inference service. This supersedes the earlier hold on production tasks. Physical performance, quality, memory/thermal and supported-tier findings remain required release gates; `probe_status: pending` is retained honestly. Do not publish the app or claim those gates passed without evidence.
