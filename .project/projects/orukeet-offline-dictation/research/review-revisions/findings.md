---
type: research_findings
project: orukeet-offline-dictation
slug: review-revisions
created: 2026-09-15T12:15:07Z
updated: 2026-09-15T12:26:20Z
---

# Findings: Orukeet plan review revisions

## Source References

- Repository: `AudioRecorder.kt`, `AudioSessionCoordinator.kt`, `VoxtralDictationManager.kt`, `CloudAiAvailabilityPolicy.kt`, `FlorisApplication.kt`, `benchmark/`, `app/build.gradle.kts`, `.github/workflows/android.yml`, and `.project/context/play-store-usps.md`.
- [sherpa-onnx 1.13.4 release metadata](https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/tags/v1.13.4), [Android arm64 build](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.4/build-android-arm64-v8a.sh), [Kotlin loader](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.4/sherpa-onnx/kotlin-api/OfflineRecognizer.kt), and [native transducer loader](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.4/sherpa-onnx/csrc/offline-transducer-model.cc).
- [ORT model parsing](https://github.com/microsoft/onnxruntime/blob/v1.27.0/onnxruntime/core/graph/model.cc) and [external tensor loading](https://github.com/microsoft/onnxruntime/blob/v1.27.0/onnxruntime/core/framework/tensorprotoutils.cc).
- [Pinned model manifest](https://huggingface.co/oruk/orukeet/resolve/55a984d46f68323301837194ce647c702f55facc/onnx/manifest.json), [revision file tree](https://huggingface.co/api/models/oruk/orukeet/tree/55a984d46f68323301837194ce647c702f55facc/onnx?recursive=true&limit=100), [export requirements](https://github.com/Oruk-AI/orukeet/blob/main/export/onnx/requirements.txt), and [per-language scores](https://github.com/Oruk-AI/orukeet/blob/main/docs/current-checkpoint-benchmarks.md).
- [Android isolated services](https://developer.android.com/guide/topics/manifest/service-element), [APK splits](https://developer.android.com/build/configure-apk-splits), [GitHub release assets](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases), and [Hugging Face rate limits](https://huggingface.co/docs/hub/en/rate-limits).

## Observations

1. Activation needs its own memory gate. However, the review's universal whole-file-buffer claim is incorrect for this version: Kotlin with `assetManager = null` calls `newFromFile`; the native transducer passes model paths directly to ORT. The AssetManager branch uses `ReadFile`. ORT still parses embedded tensors and allocates runtime state, so path loading is not proof of low peak memory. The 653,182,378-byte encoder makes allocation overlap material. A 2-3x stress estimate is about 1.22-1.83 GiB before other overhead, not an established lower bound or measured peak. External-data export and direct ORT are contingencies, each requiring new validation; direct ORT alone does not remove embedded-weight parsing.
2. Release metadata confirms sherpa-onnx 1.13.4 was published 2026-07-07. Its arm64 build defaults to ORT 1.27.0, matching the export requirements. The standard AAR is 48,847,529 bytes, with published SHA-256 `03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780`. This is the compressed dependency size, not the final arm64 APK delta. Binary inventory, digest verification, operators, and page alignment still need the probe.
3. The independently fetched manifest matches SHA-256 `7e80f93f0e9b923c392424b0f85d28a717feee0a4d2a6aa9bfa723693868e727`. Its seven payload files total 671,619,800 bytes. But the pinned `onnx/` tree exposes only README, manifest, and archive. Individual resolve URLs cannot be assumed. Choose an Ownkey GitHub Release mirror, prepared by verifying/extracting the archive off-device and preserving every payload hash. No mirror was created in this task.
4. Per-file transfer adds 184,812,215 bytes relative to the archive and removes 486,807,585 bytes of phone staging. With a 256 MiB reserve, first-install free space is 940,055,256 bytes, about 940 MB / 897 MiB. Updates require the candidate plus reserve in addition to retained installed bytes. Device extraction and bzip2 dependency/test work can be removed; release preparation still validates its source archive.
5. `stopAndRead()` reads the M4A into `AudioRecording.bytes` and deletes its cache file. A file-descriptor design therefore requires an explicit file-backed recording payload with ownership/cleanup changes. Minimal secondary-process startup is still necessary because normal application startup clears cache.
6. `AudioSessionMode` only has mock and configured-provider. The cloud-named availability policy currently enforces editor/secure/incognito restrictions, not connectivity. Generalize that shared editor policy without weakening restrictions, then keep local-model and cloud-provider readiness separate.
7. Current benchmarks cover app startup and idle power, not the stated typing/keyboard-open metric. Build a reproducible host/input benchmark in the standalone probe, then move it into `benchmark/` after go. Test untouched Ownkey under concurrent probe load first; full routing/editor acceptance belongs to the integrated build.
8. Upstream does publish a Dutch figure: FLEURS Dutch has 364 recordings, 5.60% WER and 1.93% CER for Orukeet. This is read-speech checkpoint evidence, not validation of the pinned Android INT8 artifact, names/numbers, or noisy dictation. Keep the local Dutch gate and separate subset reporting; do not predict failure as fact.
9. An isolated service has no permissions of its own. FD-to-path compatibility and the isolated UID's file-access restrictions need a bounded experiment. Retaining the empty recognizer for 2-5 minutes can reduce repeated cold loads, but memory pressure and background process death still apply. Hide must continue to cancel the audio session immediately.
10. The existing Play caveat prohibits describing cloud AI as local. It should remain for cloud routes; release copy must add the verified local-dictation case and the local-audio/cloud-text distinction for voice rewrite.

## Options Considered

| Option | Pros | Cons | Decision |
| --- | --- | --- | --- |
| Per-file GitHub Release mirror | No phone extraction; per-file retry and existing hashes | Mirror preparation and delivery verification | Planned public download origin |
| Keep tar.bz2 on phone | Lower transfer size | Decompression, dependency, staging, responsiveness cost | Remove from Android design |
| Filename loader with sherpa | Existing TDT decoder; avoids AssetManager input buffer | Embedded model parsing still consumes memory | First probe candidate |
| External-data export | May allow mapped weights | New artifact, loader/FD constraints, fresh quality evidence | Contingency if activation fails |
| Direct ORT Android plus Kotlin TDT decoding | More control over sessions and buffers | Features, normalization, decoder/token parity and native cancellation work | Contingency with a separate estimate |
| Isolated inference process | Separate UID and no INTERNET permission | Descriptor/path and lifecycle compatibility work | Half-day comparison inside Stage A; prefer if it passes |

## Fold-Forward Candidates

All changes below have been folded into the planned contracts. No production task or device gate is complete.

| Finding | Target Artifact | Proposed Change |
| --- | --- | --- |
| Activation, retention, runtime and distribution | `plan.md`, `decisions.md` | Explicit loader, package/mirror choices, separate memory gates, fallback branches |
| Probe scope and measurement | `plan.md`, `spec.md` | Standalone harness; 3-5 day probe; 20-30 day total; defined typing metric |
| Recorder, policy and failure cases | `spec.md`, `plan.md` | File ownership, local session mode, shared editor policy, AC-013 through AC-016 |
| Corrections and pending measurements | `spec.md`, update note | Record source findings without marking the device probe complete |

## Open Questions

- Device activation/steady-state memory, native cancellation, supported tier, isolated-service compatibility, and retention profile remain unmeasured.
- The mirror's anonymous transfer/range behavior, actual AAR contents/ABI sizes, and final signed APK/AAB deltas require delivery evidence.
