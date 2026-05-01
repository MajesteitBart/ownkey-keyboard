---
name: AI Dictation Rewrite
status: planned
lead: ownkey-keyboard-team
created: 2026-05-01T11:07:28Z
updated: 2026-05-01T11:07:28Z
linear_project_id:
risk_level: high
spec_status_at_plan_time: active
---

# Delivery Plan: AI Dictation Rewrite

## What Changed After Probe
No probe completed yet. Initial discovery suggests the MVP should start with a provider abstraction plus preview-first UX, not an automatic post-dictation rewrite.

## Architecture Decisions
- Treat AI rewrite as a separate post-processing pipeline, not part of Voxtral transcription.
- Capture rewrite target by explicit priority: selected text, last dictation transcript, bounded recent text before cursor.
- Keep the first user-facing action explicit and preview-first.
- Use active keyboard subtype as the primary language signal.
- Design provider abstraction from day one: cloud provider first, local/on-device provider later.
- Reuse the secure secret-storage pattern from `VoxtralSecretsStore` for any user-provided provider keys.
- Keep text replacement logic independent from provider logic so editor safety can be tested without network/model dependencies.

## Probe-Driven Architecture Changes
Required before implementation approval:
1. Editor replacement probe: prove selected-text and last-dictation replacement can be done safely across common apps.
2. Provider probe: compare cloud completion API vs ML Kit Proofreading/Rewriting for voice-like snippets.
3. Language probe: validate Dutch and EN behavior, including active subtype preservation.
4. UX probe: decide where the action belongs in keyboard UI and how preview/apply/cancel appears without disrupting typing.
5. Scope probe: recover or clarify the truncated second feature request.

## Workstream Design
- **WS-1 Product Scope & Evaluation**: define rewrite behavior, voice-artifact corpus, language requirements, and the missing second feature.
- **WS-2 Provider & Privacy Architecture**: provider interface, key storage, request minimization, local/cloud decision matrix.
- **WS-3 Editor Replacement & UX**: target-range detection, preview/apply/cancel, transactional replacement, action placement.
- **WS-4 Prototype & Quality Gates**: provider prototype, app compatibility test matrix, release gates, telemetry/logging guardrails.

## Milestone Strategy
1. M1 Discovery and probes: finish scope clarification, evaluate provider options, and test editor replacement.
2. M2 MVP cloud rewrite: implement explicit action, preview, provider config, and safe replacement behind a flag/debug setting.
3. M3 Local provider experiment: add ML Kit Proofreading/Rewriting feature detection and optional use on supported devices.
4. M4 Beta hardening: polish UX, language edge cases, privacy copy, and compatibility matrix.

## Rollout Strategy
- Start behind internal/debug feature flag.
- Dogfood only with explicit provider setup and privacy warning.
- Enable for beta users after replacement safety and logging review.
- Keep local/on-device path optional until availability and quality are proven.

## Test Strategy
- Unit tests for prompt/request construction, language mapping, provider errors, and target-range selection.
- Fake provider tests for preview and replacement flows.
- Instrumented/manual tests across common apps and editor behaviors.
- Corpus-based evaluation of voice-like snippets in EN and NL for meaning preservation, artifact removal, and language preservation.
- Security/privacy review for key storage, logs, backups, and network request construction.

## Rollback Strategy
- Feature flag disables rewrite action completely.
- Provider failures leave original text untouched.
- Settings allow API key clearing and provider disabling.
- If local model availability causes instability, disable local provider independently from cloud provider.

## Remaining Delivery Risks
- IME text replacement can behave inconsistently across apps.
- Cloud AI inside a keyboard raises high privacy expectations and requires careful UX copy.
- LLM rewrites can change meaning subtly.
- Dutch support may vary across local/on-device APIs.
- Beta GenAI APIs may change or be unavailable on target devices.
- The second requested feature is not fully captured yet.
