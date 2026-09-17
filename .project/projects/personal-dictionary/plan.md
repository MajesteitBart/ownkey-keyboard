---
name: Personal Dictionary and Filler Removal
status: planned
lead: ownkey-keyboard-team
created: 2026-09-17T20:22:05Z
updated: 2026-09-17T20:44:23Z
linear_project_id: ""
risk_level: medium
spec_status_at_plan_time: planned
---

# Delivery Plan: Personal Dictionary and Filler Removal

## Status and Technical Context

Research is complete; the device prototype is pending. Static API/binary compatibility does not establish mobile quality or performance. Android baseline: Orukeet PR #13 (`fdac513d`, plus resource-only repair `2a3099e5`). Windows reference: `ba94c11bd7f31a6e053714e7c3aa39f5638b9439`. See [findings](research/windows-parity/findings.md).

The existing typing dictionary represents words/frequency/locale/shortcuts. Transcription uses `TranscriptionClient` or `OfflineDictationController`; ordinary dictation reaches `VoxtralDictationManager.stopAndInsertTranscript`. Orukeet uses greedy search and a model-ID-only engine cache.

## Architecture Decisions

1. **Separate speech storage:** dedicated Room entities/repository for stable IDs, normalized uniqueness, saved order, vocabulary, correction pairs, and typed settings. Publish immutable snapshots through `StateFlow`; save atomically. Leave typing schemas and suggestions unchanged.
2. **Pure Kotlin cleanup:** port Windows fixtures and semantics with escaped literal rules, Unicode-aware boundaries, literal replacements, and filler removal before ordered corrections, including cascades. Compile only when settings change, off the UI/IME thread.
3. **Purpose-aware integration:** snapshot at recording start and send vocabulary to ASR. Clean ordinary text on a worker dispatcher before `OrdinaryDictationCommitOperation`; recheck validity immediately before commit. Distinguish cleaned-to-empty from ASR failure. Rewrite instructions bypass cleanup.
4. **Per-stream Orukeet hints:** extend bounded IPC with vocabulary/profile. Probe modified beam search, four active paths, BPE vocabulary, and score 1.5 as Windows starting values. Keep one loaded engine; cache by model/profile if needed. Pass words through `createStream(hotwords)` without reloading on each edit.
5. **Explicit cloud capability:** known Mistral uses `context_bias`; explicitly configured compatible endpoints can use `prompt`. Unknown/custom endpoints default to no hint field. Preserve model/language/credentials/cancellation; OpenAI compatibility alone is insufficient evidence of hint support.
6. **Versioned backups:** add a dictionary/settings section. Validate before transactional restore; invalid/newer incompatible data fails visibly without clearing entries. Missing sections in older backups preserve existing data.

Avoid nested speech JSON in JetPref. The saved-voice incident makes real-file persistence coverage essential; a separate repository also avoids overloading typing semantics.

## Policy and Artifact Map

`.project/` remains authoritative. Spec/plan stay planned; no feature implementation or Linear writes are part of this task. `spec.md` defines acceptance, this plan defines sequence/gates, `decisions.md` records rationale, and `research/windows-parity/` records evidence. Decompose executable tasks after scope acceptance and the probe. `ownkey-keyboard-team` owns all phases; this does not authorize delegated agents.

## Device Probe

Decide whether empty vocabulary retains greedy search, switching to beam for hints, or whether one beam profile is acceptable throughout. Default proposal: retain greedy without hints. Measure engine recreation and keep only one model resident.

Compare identical English/Dutch recordings with no hints, relevant names, and irrelevant names. Include ordinary speech, mixed languages, and negative examples. Record name accuracy, word error rate, false insertions, warm/cold time, cancellation, and peak PSS. Publish aggregate evidence, not private audio/transcripts.

Start with a proposed 64 KiB UTF-8 vocabulary IPC budget, below the full Binder transaction budget. This is not a provider limit. Establish entry/length policy through measurement; report oversize hints without silently truncating saved words. Check slash, colon, newline, control characters, accents, and punctuation-bearing names against native hotword syntax.

## Delivery Sequence

| Phase | Component/file ownership | Dependency | Exit evidence |
| --- | --- | --- | --- |
| 0. Compatibility/device probe | `lib/offline-asr/OrukeetEngine.kt`, model/runtime inspection, private fixtures | Orukeet baseline | Paired accuracy/false-positive/time/memory results; decoder and transport decision |
| 1. Data and cleanup | New `ime/text/dictation/dictionary/` entities/repository/processor; JVM/Room tests | Agreed spec | Windows parity, Unicode/literal tests, atomic edits, disk/restart and empty-language persistence |
| 2. Settings and backup | AI dictionary/filler pages, `VoxtralScreen.kt`, navigation, `DictionaryScreen.kt`, `BackupScreen.kt`, `RestoreScreen.kt`, EN/NL resources | Phase 1 | Add/edit/delete, correction toggle, Save/Cancel, accessibility, real export/import and old-backup compatibility |
| 3. Dictation integration | `TranscriptionClient.kt`, `VoxtralDictationManager.kt`, `TranscriptionOperations.kt`, `OfflineDictationController.kt`, `InferenceConnection.kt`, `InferenceService.kt`, `OrukeetEngine.kt` | Phase 0 decision and phase 1 snapshot | Per-session hints, request fixtures, local inference, ordinary-only cleanup, neutral empty result, cancellation/editor safety |
| 4. Qualification | Tests, physical-device checks, builds, project evidence | Phases 2–3 | Upgrade/restore persistence, no typing regression, local/cloud dictation, required CI |

Cleanup remains usable with endpoints lacking hints. Keep unqualified local hotwords off rather than silently altering ordinary dictation. Milestones: scope/probe; durable data and cleanup; UI/backup/integration; internally installable qualified build.

## Rollout

Implement after Orukeet review/merge or explicitly retain it as a branch dependency. Start with empty entries and no automatic typing-word migration. Qualify internally with Windows filler defaults, clear disable controls, and cloud vocabulary disclosure. Explain unsupported endpoints/system voice input. Public Orukeet enablement keeps its separate existing gates.

## Test Strategy

- Port every Windows cleanup assertion; add Unicode/accents, apostrophes/hyphens, punctuation-bearing names, literal dollar signs/backslashes, ordered cascades, paragraphs, and custom protected-word overrides.
- Cover stable IDs/order, normalized duplicates, atomic add/edit/remove, process recreation, non-destructive migration, and actual disk persistence.
- Test real backup export/import, empty languages, corrupt/unknown versions, missing older-backup sections, and preservation of typing entries/rewrite voices.
- Assert exact provider multipart fields, omission when disabled, bounded IPC, immutable session snapshots, and no vocabulary/transcript logging.
- Exercise filler-only speech, rewrite instructions mentioning fillers, cancellation during cleanup, editor changes, service death, and no hidden cloud fallback.
- Check TalkBack/large text, keyboard-open/typing performance, upgrade/restart/force-stop, and physical arm64 accuracy/latency/memory. Emulator performance alone is insufficient.
- Run affected JVM/instrumentation tests, debug/release compile/resource processing, required CI, `git diff --check`, and `bash .agents/scripts/pm/validate.sh`. Record actual results and pending checks distinctly.

## Rollback and Risks

Keep processing bypass and local-hint controls independent of stored data/provider choice. No rollback deletes or reinterprets the speech database. Independently version backups and preserve storage on incompatible restore. Verify ordinary keyboard/AI settings still open.

Main risks: mobile beam-search cost, mixed-language false deletions, JVM boundary/casing differences, unknown endpoint limits, and backup/migration omissions. Resolve through the probe, explicit conservative language lists, parity/negative cases, visible limits, and real upgrade/restore tests.
