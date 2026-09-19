---
type: research_progress
project: personal-dictionary
slug: windows-parity
created: 2026-09-17T20:22:35Z
updated: 2026-09-17T20:44:23Z
---

# Progress: Windows Dictionary and Filler Cleanup Parity

## 2026-09-17

- Published existing Orukeet/saved-voice/setup work as draft PR #13 before completing dictionary planning.
- Read both requested T3 threads through supported CLI/API access. Kept raw exports outside the repository.
- Inspected the Windows dictionary form, normalization, provider hints, local decoding, cleanup pipeline, and regression fixtures at `ba94c11bd7f31a6e053714e7c3aa39f5638b9439`.
- Inspected Android typing dictionaries, AI/transcription/session flows, local service/model/runtime, and backup/restore.
- Confirmed packaged hotword API availability with `javap`; cross-checked pinned upstream source and Mistral's official API contract.
- Opened project and research intake through Delano commands. Replaced generated placeholders with findings, planned spec, delivery sequence, and decisions.
- Kept dictionary research on `plan/android-personal-dictionary`; no Android dictionary implementation was made.

## Validation Evidence

- Windows: `py -3 -m unittest discover -s tests -p test_text_cleanup.py` — 15 tests passed.
- Research initialization: `bash .agents/scripts/pm/validate.sh` — zero errors/warnings.
- Post-fold validation: `bash .agents/scripts/pm/validate.sh` passed with final summary of zero errors/warnings. Its dry-run sync subchecks report existing unrelated GitHub/Linear references without inspected counterparts; no external state was changed.
- Not performed: Android dictionary tests, live provider calls, physical-device hotword inference, or performance qualification. Static compatibility is not runtime qualification.

## Handoff Summary

Research question answered and conclusions folded into `spec.md`, `plan.md`, and `decisions.md`. Proposed sequence: device probe; typed storage/cleanup; settings/backup; transcription integration; qualification. Implementation remains planned. Original source checkouts and unrelated local edits were preserved.
