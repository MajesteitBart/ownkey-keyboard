---
type: research_intake
project: voice-prompt-rewrite
slug: battery-observability
owner: ownkey-keyboard-team
status: closed
created: 2026-08-06T06:25:15Z
updated: 2026-08-06T06:25:15Z
---

# Research Plan: Battery Observability and Drain Audit

## Goal

Identify avoidable Ownkey battery drains and define a repeatable, content-safe way to correlate app work with device power use.

## Primary Question

Which Ownkey code paths can cause avoidable battery drain, and what minimum instrumentation makes IME power use repeatably traceable without content telemetry?

## Scope

### In Scope

- Main Android IME process lifecycle, clipboard cleanup, microphone sampling, and provider network work.
- Existing profileability and benchmark infrastructure.
- Local developer traces and benchmarks that contain no typed, selected, dictated, or generated content.

### Out of Scope

- Production analytics or remote battery telemetry.
- Claiming device-wide power-rail measurements are exact per-app energy figures.
- Physical-device energy numbers without a supported device attached.
- Wear OS optimization in this first pass.

## Current Phase

Closed and folded forward to T-021.

## Phases

- [x] Open research intake
- [x] Inspect lifecycle, background work, provider I/O, manifest, and benchmark configuration
- [x] Compare the proposed capture path with current Android guidance
- [x] Fold durable findings into the active spec, plan, WS-E, T-019, and T-021

## Decisions Made

| Decision | Rationale |
| --- | --- |
| Use Macrobenchmark `PowerMetric` plus Perfetto/System Trace | This is the current Android-supported repeatable power workflow and produces inspectable traces. |
| Keep all trace labels fixed and content-free | Ownkey must not log keyboard, audio, prompt, endpoint, or provider-response content. |
| Remove idle wake-ups instead of merely measuring them | The clipboard timer wakes every minute even when both cleanup options are disabled. |
| Make provider requests internally IO-bound and cancellation-aware | Every caller, including voice rewrite, must remain safe even when launched from the main IME scope. |

## Blockers

| Blocker | Owner | Check-back |
| --- | --- | --- |
| A supported physical Android device is required for real `PowerMetric`/power-rail numbers | ownkey-keyboard-team | During T-018/T-019 physical-device validation |
