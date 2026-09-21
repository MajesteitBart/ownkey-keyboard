# Ownkey ASR-only runtime

The application uses sherpa-onnx **1.13.4**, ONNX Runtime **1.27.0**, and the original
Kotlin bindings. Its JNI library is rebuilt from the pinned upstream source with TTS,
speaker diarization, C API, executables, websocket and portaudio disabled. Eigen is
compiled with `EIGEN_MPL2_ONLY`. The stock upstream AAR statically bundles unused GPL
TTS/eSpeak code; its native payload must not be used in Ownkey's distribution.

Build prerequisites: Python 3.11+, Android NDK 29.0.14206865 and SDK CMake 3.22.1 on
Linux x86_64. Install SDK components through the SDK manager, then run:

```bash
python3 tools/orukeet-runtime/build.py --sdk "$ANDROID_HOME" \
  --cache "$XDG_CACHE_HOME/ownkey-orukeet-runtime" --output /tmp/ownkey-asr-runtime
```

Choose an explicit user-owned cache directory if `XDG_CACHE_HOME` is unset. The script
verifies source, original bindings and ONNX Runtime archives; upstream CMake pins
transitive dependency hashes. It rejects changed extracted input files. A clean cache
provides the strongest reproduction check. It builds arm64-v8a and x86_64 JNI only,
checks that eSpeak/Piper sources were not fetched, strips libraries, rejects eSpeak
payload markers and writes a deterministically ordered AAR with fixed entry timestamps.
Compiler/build-path differences can change native binary hashes; consumers use the
published artifact's exact SHA-256, not a claim of cross-machine bit-for-bit identity.

Outputs: the AAR, provenance (inputs/toolchains/features/native hashes), reproduction
script, pins, and full native dependency notices. Keep the original sherpa Apache
licence and ONNX Runtime MIT/third-party notices beside these files when publishing.
ASR dependencies include kaldi-native-fbank, kaldi-decoder, kaldifst, OpenFST,
simple-sentencepiece, KissFFT, Eigen and nlohmann/json. Their licence texts are bundled
in `THIRD-PARTY-NOTICES.txt`; Eigen's unmodified source is available from the pinned
CMake archive under MPL-2.0. The app exposes notices through its licences screen.

Only `libonnxruntime.so` and `libsherpa-onnx-jni.so` are packaged. 32-bit APKs retain
ordinary keyboard/cloud features and report local dictation as unavailable. Physical
arm64 qualification still controls public enablement. The x86_64 payload supports
internal emulator verification.

Verify final APKs with `tools/orukeet-probe/check-packages.py`, then run the integrated
WAV/M4A/death/cancel/recovery tests before promoting a runtime artifact. Runtime AARs
are build-time dependencies bundled in the APK; the app downloads only pinned model
data. A changed native artifact needs a new runtime release ID and SHA-256.
