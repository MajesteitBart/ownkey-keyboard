---
id: WS-A
name: WS-A Autocorrect engine rebuild
owner: ownkey-keyboard-team
status: active
created: 2026-09-24T11:09:21Z
updated: 2026-09-24T18:38:49Z
---

# Workstream: WS-A Autocorrect engine rebuild

## Objective

Replace the autocorrect scoring core, commit path, settings surface and dictionaries so that autocorrect fixes common EN and NL touch typos with at least 97% precision, as measured by the in-repo benchmark.

## Owned Files/Areas

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/` (engine, policies, new pure-Kotlin engine package)
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt` (auto-commit candidate selection)
- Auto-commit call sites in `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
- `AppPrefs.Correction` and `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/typing/TypingScreen.kt`
- `app/src/main/assets/ime/dict/` and a new `tools/dictionary-build/`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/nlp/` and `app/src/test/resources/autocorrect/`

## Dependencies

- Baseline and harness in `research/`.
- The `personal-dictionary` project owns the dictation dictionary store. This workstream only reads it to block auto-commit of personal words.
- Glide typing reads the word list through `NlpManager.getListOfWords()` and ranks with `getFrequencyForWord()`. Keep both working, and check glide ranking whenever the frequency scale changes.
- `FlorisSpellCheckerService` calls `spell()`, which must move to the new scorer together with suggestions.
- Languages other than EN and NL load `data.json` and must keep working.

## Risks

- Heap limits on low-end devices, which already caused one startup OOM in the typo index.
- Main-thread I/O in the synchronous commit path (Room, ContentResolver, personal n-gram file loads).
- Threshold calibration on synthetic data may not transfer to real typing. The dogfood undo-rate gate covers this.
- `KeyboardManager.kt` is a shared file. T-002 and T-003 both edit it and are sequenced. Coordinate with any concurrent keyboard work before editing the auto-commit call sites.

## Handoff Criteria

- Phase gates in `plan.md` met and recorded in `updates/`.
- Benchmark runs in the unit test suite and fails on regression below the last passed gate.
- Legacy engine switch removed after one stable release on the new engine.
