---
type: research_findings
project: personal-dictionary
slug: windows-parity
created: 2026-09-17T20:22:35Z
updated: 2026-09-17T20:44:23Z
---

# Findings: Windows Dictionary and Filler Cleanup

## Conclusion

Port three distinct features: recognition vocabulary, exact spelling corrections, and deterministic filler cleanup. No cleanup LLM or new model weights are required. Android already has the needed native hotword API and BPE file, but currently does not use them. Mobile beam-search quality/cost must be measured before shipping hints. Corrections and filler removal can run locally for any transcription performed inside Ownkey.

## Source References

Read both requested threads through T3 Code's supported CLI/API; extracted requirements below are paraphrased. Raw thread exports remain outside the repository.

- `2579f1a2-6442-4380-8616-114755807d84`, **Build Orukeet Implementation**, September 15–16, 2026. September 16 discussion requests personal vocabulary and filler cleanup without another model or LLM overhead, while retaining Orukeet.
- `b250e39f-ae6f-4a62-ac4c-9d63875f9067`, **Redesign Ownkey Settings Page**, September 16, 2026. Covers the word/correction toggle, implementation, and review fixes preserving empty language selection, newlines, and intentional casing. Windows PR #5 was merged as `a6c51186ec5e36f5fcd91e5e6bb0f1d7b2898b06`; subsequent release baseline is `ba94c11bd7f31a6e053714e7c3aa39f5638b9439`.
- Windows baseline [cleanup](https://github.com/MajesteitBart/ownkey-windows/blob/ba94c11bd7f31a6e053714e7c3aa39f5638b9439/text_cleanup.py), [cleanup tests](https://github.com/MajesteitBart/ownkey-windows/blob/ba94c11bd7f31a6e053714e7c3aa39f5638b9439/tests/test_text_cleanup.py), [settings/pipeline](https://github.com/MajesteitBart/ownkey-windows/blob/ba94c11bd7f31a6e053714e7c3aa39f5638b9439/ownkey.py), [local recognizer](https://github.com/MajesteitBart/ownkey-windows/blob/ba94c11bd7f31a6e053714e7c3aa39f5638b9439/local_transcription.py), and [provider adapters](https://github.com/MajesteitBart/ownkey-windows/blob/ba94c11bd7f31a6e053714e7c3aa39f5638b9439/providers.py). Inspected provider/local integration tests and the local dictionary UI screenshot too.
- Android [Orukeet PR #13](https://github.com/MajesteitBart/ownkey-keyboard/pull/13), source behavior baseline `fdac513d`: files listed in the Android findings below. The subsequent `2a3099e5` repair moves Dutch resources without changing behavior.
- Pinned sherpa-onnx v1.13.4 [Kotlin API](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.4/sherpa-onnx/kotlin-api/OfflineRecognizer.kt) and [NeMo transducer implementation](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.4/sherpa-onnx/csrc/offline-recognizer-transducer-nemo-impl.h). [Upstream PR #3077](https://github.com/k2-fsa/sherpa-onnx/pull/3077) added NeMo/TDT modified beam search and hotwords.
- [Official Mistral transcription API](https://docs.mistral.ai/api/endpoint/audio/transcriptions): `context_bias` vocabulary array. Do not assume a universal 100-word provider limit; neither the inspected contract nor Windows thread supports imposing that claim.

## Windows Behavior Observed

| Feature | Stored data | Processing |
| --- | --- | --- |
| Vocabulary | Unique names/phrases | Biases recognition; it does not promise exact replacement |
| Corrections | Ordered source/replacement pairs | Case-insensitive whole-word literal substitutions after transcription |
| Filler words | Master switch, selected languages, custom terms | Local rules before corrections; no LLM |

The Dictionary form has a `Correct a misspelling` toggle. Off: one word/phrase field. On: source and replacement. Case-only corrections are allowed. Entries normalize whitespace and deduplicate case-insensitively. The UI distinguishes hints and fixes. Android should reuse that interaction in Compose, not reproduce the desktop sidebar.

Defaults: empty vocabulary/corrections, filler removal enabled, English and Dutch selected. An explicitly empty language list remains empty. Missing/malformed settings use defaults. German, French, and Spanish are optional lists.

| List | Built-in filler inventory | Words protected when this language is selected |
| --- | --- | --- |
| English | uh, uhh, um, umm, uhm, erm, er, mhm | none |
| Dutch | uh, uhm, eh, ehm, euh, euhm, hm | er, este |
| German | äh, ähm, ehm, hm, mh | um, er, este |
| French | euh, heu, hum | este |
| Spanish | eh, em, ehm, este | er |

Protections are subtracted from the combined enabled lists. Custom removals are added afterwards, so they can explicitly override protection. This is configured language handling, not automatic semantic language detection. Selecting Spanish alone can still remove meaningful `este`; porting rules does not make them context-aware. The UI must make language choices and the off switch clear.

Filler matching protects word boundaries, apostrophes, and hyphens, handles repeated hesitations and punctuation, preserves line/paragraph breaks, and capitalizes only plain lowercase starts. `like`, `well`, `dus`, and `gewoon` are deliberately absent. `Uh-huh` is retained. Pause commas may remain: Windows changes `I think, uh, we should ship it.` to `I think, we should ship it.`. Do not promise grammatical rewriting or semantic self-correction.

Correction patterns escape sources and insert targets literally. They run in saved order and can cascade (A → B followed by B → C yields C). Retain this behavior for parity; longest-match/non-cascading rules would be a separate product change. JVM Unicode boundaries require additional tests.

Windows ordinary dictation runs filler removal then corrections. Its rewrite-command branch occurs before cleanup; spoken rewrite instructions still receive vocabulary hints but bypass cleanup. Selected source text and final rewritten text are not globally transformed by the dictionary.

The local recognizer uses modified beam search, four active paths, BPE vocabulary, hotword score 1.5, and per-stream hotwords. The transport joins terms with `/` and replaces slashes within terms with spaces. Mistral sends vocabulary using `context_bias`; other adapters use provider-specific hints such as `prompt`. Desktop settings/provider breadth does not imply Android supports the same providers.

## Android Findings

Paths below are relative to the Android repository. App Kotlin paths share `app/src/main/kotlin/dev/patrickgold/florisboard/`; runtime paths share `lib/offline-asr/src/main/kotlin/org/ownkey/offline/`.

- `app/settings/dictionary/DictionaryScreen.kt`, `ime/dictionary/UserDictionary.kt`, and `DictionaryManager.kt`: existing system/Ownkey typing dictionaries use word/frequency/locale/shortcut data. Do not overload them with correction pairs or silently send their contents to ASR.
- `OrukeetEngine.kt`: greedy decoder and `createStream()` with no hotwords; model configuration lacks BPE hint setup. `ModelCatalog.kt` already downloads `bpe.vocab` (117,408 bytes) with pinned integrity metadata.
- `lib/offline-asr/build.gradle.kts` and `tools/orukeet-runtime/pins.json`: packaged runtime is `1.13.4-asr1`, sourced from sherpa-onnx v1.13.4 commit `142807252687d81b40d6315f23470a1512a00de3`. `javap` on the actual packaged classes confirms `createStream(String)` and decoding-method, active-path, hotword-score, modeling-unit, and BPE-vocabulary configuration APIs. No runtime replacement is justified by current evidence.
- `InferenceConnection.kt` / `InferenceService.kt`: requests currently carry model/request/audio, not hints. Engine cache is keyed only by model. Carry a bounded snapshot, safely encode terms, and account for decoder-profile changes without keeping two models in memory.
- `ime/text/dictation/offline/OfflineDictationController.kt`: current session submits model ID and audio. Extend the session/request contract with immutable hints; avoid per-request preference reads in the inference process.
- `ime/text/dictation/TranscriptionClient.kt`: distinguishes ordinary dictation and spoken rewrite instructions. Cloud multipart currently has model/language/audio but no vocabulary. Add hints through explicit adapter capability; do not guess all custom endpoints accept them.
- `TranscriptionOperations.kt` is shared. Unconditional cleanup here would damage rewrite instructions, including instructions intentionally mentioning filler words. `VoxtralDictationManager.stopAndInsertTranscript` is the ordinary insertion integration point. Preserve cancellation/lease checks immediately before commit.
- Current empty-ASR handling presents failure. A nonempty transcript cleaned to empty needs a separate neutral result rather than reusing that failure path.
- `TranscriptionBackend.kt`: external/system IME handoff is outside Ownkey transcription control. Ownkey cannot promise hints or post-processing there.
- `app/settings/advanced/BackupScreen.kt` / `RestoreScreen.kt`: existing backup covers preferences and selected assets; a new database needs explicit export/import integration. Use a versioned portable section and transactional restore.
- Saved-voice recovery in PR #13 demonstrates nested JetPref JSON failure modes. Use typed speech storage and real-file lifecycle tests rather than another nested preference blob.

## Options and Decisions

| Option | Benefit | Cost/risk | Proposal |
| --- | --- | --- | --- |
| Reuse typing dictionary tables | Less new storage | Wrong semantics; mixes typing and cloud vocabulary | Reject |
| Dedicated speech repository (implemented as a versioned JSON document with atomic writes) | Typed records; clear scope and backup contract; real-file JVM tests | New file format and export section | Adopt |
| LLM cleanup or training | Could handle semantics | Extra model/network/latency; not requested | Reject |
| Deterministic Windows rules | Fast, offline, testable parity | No semantic understanding; language false positives | Adopt with explicit controls |
| Beam hints using current model/runtime | No extra model download | Mobile accuracy/time/RAM unmeasured | Device probe required |
| One beam profile for all local speech | No profile-switch reload | Changes performance even without hints | Probe against greedy-without-hints |
| Single-pass non-cascading corrections | Avoids chains | Diverges from Windows saved-order semantics | Defer |

## Evidence and Limits

`py -3 -m unittest discover -s tests -p test_text_cleanup.py` passed all 15 tests in the Windows checkout. This verifies the reference processor, not Android behavior. Thread/source/screenshot inspection and packaged-API inspection are complete. No Android personal-dictionary code, live provider call, or mobile hotword benchmark was performed during research.

## Fold-Forward

Findings are incorporated into `spec.md`, `plan.md`, and `decisions.md`. Remaining implementation probes: paired physical-device quality/performance, profile caching, safe hotword syntax/budgets, exact provider multipart behavior, and Android Unicode parity. None blocks completing this research plan; none is claimed passed.
