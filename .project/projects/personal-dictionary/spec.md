---
name: Personal Dictionary and Filler Removal
slug: personal-dictionary
owner: ownkey-keyboard-team
status: planned
created: 2026-09-17T20:22:05Z
updated: 2026-09-17T20:44:23Z
outcome: Android dictation uses durable vocabulary hints, spelling corrections, and optional local filler removal without disrupting typing.
uncertainty: medium
probe_required: true
probe_status: pending
---

# Spec: Personal Dictionary and Filler Removal

## Executive Summary

Bring three Windows features to Ownkey Keyboard: names and terms that help speech recognition, explicit spelling corrections after transcription, and filler removal without an LLM. This is a proposal; the current request is research and planning before implementation.

See [research findings](research/windows-parity/findings.md) and the [delivery plan](plan.md). Build on [Orukeet PR #13](https://github.com/MajesteitBart/ownkey-keyboard/pull/13), including its saved-voice fixes and existing setup flow.

## Problem and Users

People dictating names, product terms, and mixed Dutch/English speech need consistent spelling and fewer hesitations. Android's existing user dictionary supports typing suggestions, not speech vocabulary, correction pairs, or filler cleanup.

## Outcome and Success Metrics

- Port all assertions from the 15 Windows cleanup tests, with additional Android lifecycle, Unicode, and session-safety coverage.
- Preserve data through force-stop, process death, restart, upgrade, and backup/restore. An explicitly empty language selection stays empty.
- No network request or language model for cleanup. Proposed device budget: p95 at most 10 ms for 10,000 characters and 100 correction rules, off the main thread.
- No dictionary disk access, pattern compilation, or inference on the typing path. Proposed gate: keyboard-open and typing latency p95 within 5% of baseline under repeatable equivalent measurements.
- Local hotwords improve target-name recognition on fixed English/Dutch audio without material ordinary-speech regression. Record accuracy, false insertions, latency, and peak memory before choosing a decoder profile.

## User Stories

- US-001: Save a name or term to help supported recognizers spell it correctly.
- US-002: Save a recurring mishearing and its replacement.
- US-003: Remove hesitations such as “uh” and “ehm” while preserving meaningful words and paragraphs.
- US-004: Edit, disable, and back up these choices without losing them during ordinary use or updates.

## Acceptance Scenarios

- AC-001: A saved vocabulary word is included in supported ASR hints on the next recording. Hints improve recognition but do not guarantee spelling.
- AC-002: With `own key` → `Ownkey` and English/Dutch filler removal, `Um, own key uh works.` inserts `Ownkey works.`.
- AC-003: With `bart` → `Bart`, `bartender` is unchanged. Case-only corrections are allowed and replacement text is literal, including dollar signs and backslashes.
- AC-004: Cleanup preserves line/paragraph breaks. `Um, iPhone works.` becomes `iPhone works.` without changing intentional casing.
- AC-005: Enabled Dutch protects `er`; enabled German protects `um`. `like`, `well`, `dus`, and `gewoon` are not built-in fillers. Custom removals can deliberately override protections.
- AC-006: Selecting no languages persists and removes only custom fillers. Turning filler removal off preserves hesitations while explicit corrections remain active.
- AC-007: Filler-only speech inserts nothing and produces a neutral no-text result, not a recognition failure.
- AC-008: Spoken rewrite instructions receive recognition hints but bypass cleanup. Selected text and LLM output are outside this feature.
- AC-009: Cancellation/editor changes during cleanup prevent stale insertion. Editing the dictionary during recording affects the next recording.
- AC-010: Fresh installs have empty speech entries, filler removal on, and English/Dutch selected. Typing dictionaries and rewrite voices survive upgrade/restore unchanged.
- AC-011: Cloud hints use a supported, enabled format with disclosure that words accompany audio to the endpoint. System voice-input handoff is identified as outside Ownkey's cleanup control.

## Scope

### In Scope

Android phone/tablet settings; vocabulary words/phrases; correction pairs; filler master switch, five Windows language lists and custom removals; Ownkey-controlled local/cloud dictation; persistence and application backup; accessible Compose UI; English/Dutch copy; physical-device qualification.

### Out of Scope

Model training, a cleanup LLM, semantic self-correction, changing typed text, automatic word collection, copying typing dictionaries into requests, cross-device sync, Windows settings import, Wear OS, new providers, public Orukeet enablement, and setup/rewrite-voice changes.

## Functional Requirements

1. Link `Personal dictionary` and `Filler words` from AI settings. Link the same speech page from existing Dictionary settings, clearly distinguishing it from typing dictionaries.
2. Match the Windows add form: one word field; `Correct a misspelling` switches to source/replacement fields. Validate before Save; Cancel/Back preserves prior entries.
3. Normalize whitespace and case-insensitive duplicate keys; preserve display spelling. Allow case-only corrections; reject blanks and exactly identical pairs. Label words and corrections distinctly.
4. Preserve Windows order: fillers first, then literal whole-word case-insensitive corrections in saved order. Preserve target casing and test cascading rules explicitly.
5. Protect meaningful words according to selected languages, not inferred ASR language. Explain language selection and deliberate custom overrides.
6. Clean ordinary dictation once before editor commit. Never clean shared transcription results unconditionally.
7. Snapshot all vocabulary/settings at recording start and retain that immutable snapshot through transcription and commit.
8. Persist transactionally in a dedicated speech repository; no destructive migrations or silent resets. Export a versioned backup section. Older backups without it preserve current speech data.
9. Bound requests/IPC and encode native hotword syntax safely. Treat entries literally. Report oversize/unsupported hints without silently truncating saved data.
10. Preserve provider choice, disclosures, password/incognito restrictions, cancellation, and editor/session guards. No hidden cloud fallback or vocabulary/audio/transcript logging.

## Non-Functional Requirements

Use existing Compose, Room, navigation, and coroutines. Compile/cache snapshots off the UI/IME thread. Keep at most one loaded Orukeet engine. Support TalkBack, large text, and narrow layouts. Honor Orukeet's internal-build gate.

## Assumptions and Needs Clarification

Windows defaults are proposed: filler removal on, English/Dutch on, other lists off, empty speech entries. No product answer is needed to finish this plan. Review the proposed scope before implementation; decoder and endpoint capabilities require technical probes.

## Probe Findings and Remaining Unknowns

Pinned source and the packaged AAR expose beam search, per-stream hotwords, and BPE options. The manifest already includes `bpe.vocab`; no new model download is expected. Android currently uses greedy search without vocabulary. Physical-device quality, latency, memory, transport limits, and exact provider multipart behavior remain unqualified.

## Footguns and Touchpoints

Shared cleanup would alter spoken instructions. Empty language lists must not reset. Whitespace/case repair can damage paragraphs/names. A new Room database needs explicit backup integration. Decoder switching needs a cache key beyond model ID. Exercise settings, dictation, rewrite, editor changes, service death, cloud requests, backups, upgrades, and typing performance.

## Dependencies and Approval Notes

Depends on Orukeet PR #13, current transcription/session safety, backup/restore, Windows fixtures, and a representative arm64 phone. Research/planning requested on 2026-09-17; implementation remains planned and separate from the Orukeet PR.
