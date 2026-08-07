---
id: WS-C
name: WS-C Targeting and Rewrite Orchestration
owner: ownkey-keyboard-team
status: done
created: 2026-08-04T13:27:38Z
updated: 2026-08-04T22:53:56Z
---

# Workstream: WS-C Targeting and Rewrite Orchestration

## Objective

Implement safe selected/whole-field target capture, the shared cloud-AI availability gate, and the cancellable two-provider voice-rewrite session through reviewed, integrity-checked replacement.

## Owned Files/Areas

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/rewrite/LlmRewriteManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/rewrite/LlmRewriteClient.kt`
- New voice-rewrite session, target resolver/snapshot, preflight, cloud-AI availability policy, and error-mapping files
- Incognito and secure-field enforcement at the dictation, preset rewrite, and voice-rewrite entry points
- Editor integration limited to selection request/confirmation, session identity, verification, replacement, and copy fallback
- Focused JVM/integration tests with fake editor, recorder, transcription, and rewrite dependencies

## Dependencies

- WS-A approval gate
- WS-B shared audio-session and transcription-only contracts
- Existing `EditorInstance` selection/content signals and configured rewrite provider

## Risks

- Select All success can be asynchronous or incorrectly reported by host editors.
- Selection/range equality without source-content verification can write to stale text.
- Cancellation between sequential provider calls can leave stale result state.
- Reusing preset fallback behavior would silently target the previous sentence.

## Handoff Criteria

- Existing selection and confirmed Select All paths pass exact-scope tests; partial-field inference is impossible.
- Preflight blocks secure, incognito, empty, unsupported, over-limit, unconfigured, and concurrent-session cases before provider work.
- One availability policy gates all three AI entry points; no entry point keeps working in incognito because it re-derives the rule locally.
- Transcript never commits directly and retry/rerecord semantics preserve only valid in-memory state.
- Replacement is allowed only after review and target verification; mismatch exposes copy/close.
- Cancellation and lifecycle invalidation stop jobs and discard temporary data.
