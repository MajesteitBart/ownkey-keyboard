---
id: T-021
name: Instrument battery use and remove idle wakeups
status: done
workstream: WS-E
created: 2026-08-06T06:25:15Z
updated: 2026-08-06T07:03:58Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-017]
conflicts_with: []
parallel: false
priority: high
estimate: M
story_id: US-009
acceptance_criteria_ids: [AC-043]
---

# Task: Instrument battery use and remove idle wakeups

## Description

Restore Ownkey's release-like benchmark target, add a supported physical-device power benchmark and content-free trace spans, remove the unconditional clipboard cleanup timer, and ensure provider sockets are disconnected when editor/IME lifecycle cancellation occurs.

## Acceptance Criteria

- [x] The benchmark module is included, compiles against the current Android configuration, and targets `nl.bartvandermeeren.ownkey.bench` plus the actual Ownkey IME component.
- [x] A Macrobenchmark `PowerMetric` scenario is wired to produce repeatable energy/system-trace artifacts on supported API 29+ physical devices and skips unsupported hardware with a clear reason.
- [x] Perfetto/System Trace exposes fixed Ownkey spans for voice recording, voice processing, transcription requests, and rewrite requests without content, endpoint, provider body, API-key, or content-derived fields.
- [x] Clipboard cleanup performs no periodic wake-up while cleanup is disabled or history has no expiring items, and schedules the earliest enabled expiry when work exists.
- [x] Blocking provider work runs off the main/typing-critical thread and cancellation disconnects an active HTTP connection instead of waiting for the full read timeout.
- [x] Deterministic unit tests cover next-expiry scheduling, trace lifecycle balance, IO confinement, and cancellation disconnect behavior.
- [x] A developer runbook identifies benchmark package IDs, commands, trace labels, physical-device requirements, and the system-wide nature of power-rail metrics.

## Traceability

- Story: US-009.
- Acceptance criteria: AC-043; NFR-001, NFR-003, NFR-004, NFR-005, NFR-007, NFR-015.

## Technical Notes

Instrumentation is local developer tooling, not production telemetry. Trace labels must be fixed enums/constants and must never interpolate editor text, audio metadata, prompt text, endpoint URLs, provider responses, or content-derived sizes/fingerprints.

Power-rail metrics are system-wide. Use clean-device A/B runs and correlate them with Ownkey spans; do not report them as exact per-app energy accounting.

## Definition of Done

- [x] Implementation complete
- [x] Focused unit tests and benchmark compilation pass
- [x] Debug/release Kotlin compilation passes
- [x] Delano validation passes
- [x] Physical-device-only evidence gap is recorded for T-019

## Evidence Log

- 2026-08-06: Task opened from the battery-observability research intake; T-017 is done and no code dependency blocks implementation.
- 2026-08-06T07:03:58Z: Phone and Wear lifecycle/power instrumentation completed. The 280-test app suite, release compilation, benchmark test APK, release-like target APK, and both Wear compilations pass. No ADB device is attached, so supported-device energy-rail and manual voice trace captures remain T-019 evidence.
