---
type: research_intake
project: voice-prompt-rewrite
slug: windows-voice-rewrite-ux
owner: ownkey-keyboard-team
status: completed
created: 2026-08-04T08:34:03Z
updated: 2026-08-04T11:49:14Z
---

# Research Plan: Windows Voice-Instruction Rewrite UX

## Goal

Answer how Ownkey Android should combine selected text with a spoken rewrite instruction, using the Windows experience as evidence while fitting Android IME constraints across phone, tablet, success, cancellation, and failure states.

## Primary Question

How should Ownkey Android combine selected text with a spoken rewrite instruction, using the Windows experience as evidence while fitting Android IME constraints across phone, tablet, success, cancellation, and failure states?

## Scope

### In Scope

- Inspect the supplied Android compact and expanded screenshots.
- Inspect current Android selected-text rewrite, dictation, panel, editor, and lifecycle behavior.
- Inspect the public Ownkey Windows repository, UI assets, state model, settings, and selected-text rewrite pipeline.
- Inspect the public Ownkey website's Windows mockup and source.
- Compare mobile entry, recording, processing, result, cancellation, configuration, privacy, accessibility, and adaptive-layout options.
- Fold durable recommendations into `spec.md`.

### Out of Scope

- Implementing or prototyping the Android feature.
- Changing the generated delivery plan, workstreams, or tasks before spec approval.
- External sync writes.
- Publishing secrets, content, raw prompt text, or machine-specific paths.

## Current Phase

Folded forward into the draft spec.

## Phases

- [x] Open research intake
- [x] Investigate sources and options
- [x] Summarize findings
- [x] Fold durable conclusions into `spec.md`

## Decisions Made

| Decision | Rationale |
| --- | --- |
| Use long-press on the dictation key as the primary accelerator; retain the pinned card | Product review preferred the Windows-like direct shortcut. Tap remains dictation, while the card and accessibility action preserve discoverability. |
| Use existing selection or visible Select All | Product review approved whole-field rewrite when nothing is selected, provided Select All visibly confirms scope before recording or network use. |
| Keep review-before-replace on Android | It protects against transcription/model mistakes and matches the existing safe Android result flow. |
| Reuse dictation configuration through a transcription-only operation | Voice instruction needs audio-to-text but must never commit the transcript into the editor. |
| Capture early and verify again before replacement | The Windows source captures selection at gesture start; Android's longer asynchronous flow adds stale-editor risk. |
| Center state surfaces at expanded widths | The supplied tablet/split screenshots show that edge-to-edge controls become difficult to read and reach. |
| Share the first-row recording strip across dictation and voice rewrite | Product review preferred a real center waveform with pause/cancel and a trailing stop action; the repository already contains an unwired reference composition and live amplitude flow. |
| Make success/error terminal feedback explicit and bounded | Success should not be inferred from every processing-to-idle transition, and the red error button should return to idle automatically after approximately five seconds. |

## Blockers

| Blocker | Owner | Check-back |
| --- | --- | --- |
| Live Android prototype evidence for selection retention and lifecycle cancellation | ownkey-keyboard-team | Before spec activation |
| Live Android prototype evidence for tap/long-press disambiguation and Select-All compatibility | ownkey-keyboard-team | Before spec activation |
| Live-device waveform calibration, narrow-width composition, reduced-motion behavior, and terminal-state timing | ownkey-keyboard-team | Before spec activation |
| Product decision on incognito-mode behavior | product owner | During spec review |
