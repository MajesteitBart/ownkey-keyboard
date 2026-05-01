---
name: WS-2 Provider & Privacy Architecture
owner: ownkey-keyboard-team
status: planned
created: 2026-05-01T11:07:28Z
updated: 2026-05-01T11:07:28Z
linear_milestone_id:
linear_label_id:
---

# Workstream: WS-2 Provider & Privacy Architecture

## Objective
Define how OwnKey calls AI providers without locking the keyboard to one vendor or weakening privacy.

## Owned Files/Areas
- Rewrite provider interface
- Cloud provider request/response contracts
- Local/on-device provider feasibility
- Credential storage and privacy copy

## Dependencies
- Existing `VoxtralSecretsStore` pattern.
- Provider choice for MVP.
- ML Kit/AICore compatibility research.

## Conflict Risk Zones
- Shared settings screens and credential UX with Voxtral dictation.
- Network logging, crash reporting, and debug tooling.

## Handoff Criteria
- Provider interface can support cloud and local implementations.
- API keys are stored and cleared securely.
- User text and prompts are excluded from normal logs.
