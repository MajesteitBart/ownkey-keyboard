---
name: AI Dictation Rewrite
slug: ai-dictation-rewrite
owner: ownkey-keyboard-team
status: draft
created: 2026-05-01T11:07:28Z
updated: 2026-05-01T11:07:28Z
outcome: Let users clean up recently dictated or selected text with one tap while preserving language, meaning, privacy, and keyboard stability.
uncertainty: high
probe_required: true
probe_status: pending
---

# Spec: AI Dictation Rewrite

## Executive Summary
OwnKey already has an internal Voxtral dictation path that records audio, sends it to the configured Voxtral/Mistral endpoint when a user API key is available, and commits the returned transcript into the active editor. The next product step is not more transcription. It is a deliberate post-dictation correction layer: a single keyboard action that takes the recently inserted or selected text and rewrites it into cleaner grammar while preserving the user's language, intent, tone, and content.

The user problem is concrete: speech-to-text usually recognizes the words correctly, but live spoken thought often contains repeated phrases, stutters, partial starts, and wandering sentence structure. The feature should turn that rough dictated text into a sendable version without requiring copy/paste into another app.

The recommended direction is a provider-abstracted rewrite pipeline:
1. MVP cloud path using a user-provided API key, because the app already has secure API-key handling patterns for Voxtral.
2. A local/on-device provider interface from day one, because keyboard text is sensitive and Android now has credible on-device GenAI APIs for proofreading and rewriting on supported devices.
3. A preview-and-apply UX for safety, with an optional fast path later once trust is earned.

### Research anchors
- Existing OwnKey code has a Voxtral dictation manager that inserts transcripts through `editorInstance.commitText(transcript)` and stores the API key through `VoxtralSecretsStore` backed by `EncryptedSharedPreferences`. This is the strongest local implementation anchor for credential handling and dictation integration.
- Google ML Kit GenAI Proofreading API is beta, supports text entered through keyboard or voice, requires Android API 26+, returns at least one suggestion sorted by confidence, and has explicit `InputType.KEYBOARD` / `InputType.VOICE` options. Source: https://developers.google.com/ml-kit/genai/proofreading/android
- Google ML Kit GenAI Rewriting API is beta, supports short-form rewrites in styles such as Shorten, Friendly, Professional, Rephrase, and Elaborate, requires Android API 26+, and returns suggestions sorted by confidence. Source: https://developers.google.com/ml-kit/genai/rewriting/android
- Gemini Nano / AICore and Google AI Edge on-device LLM paths make local inference plausible on high-end Android devices, but availability, model download state, API maturity, language support, and latency must be probed before making local inference the MVP dependency. Sources: https://developer.android.com/ai/gemini-nano and https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android
- An external product reference, Deskdrop, validates the pattern of an Android keyboard with AI Assist, preview before apply, local/OpenAI-compatible backends, and custom rewrite commands. Source: https://github.com/SvReenen/Deskdrop

## Problem and Users
Primary user: Bart and similar OwnKey users who use voice input to capture thoughts quickly, then need to remove speech artifacts before sending.

Observed pain:
- Speech recognition often gets the words right but keeps verbal thinking artifacts.
- Users repeat themselves, restart clauses, or leave fragmented sentence structure while talking through thoughts.
- Correcting that text manually from a keyboard is slow and breaks flow.
- The rewrite must happen in the language currently used by the keyboard/subtype, not default blindly to English.

Secondary pain captured but not fully specified:
- The original feature request started to mention another keyboard behavior issue after dictation rewrite, but the available transcript is truncated at “it doesn't auto...”. Treat this as an explicit discovery item before implementation scope is finalized.

Primary users:
- People who dictate longer chat, email, and note text.
- Multilingual users, especially EN/NL users, who expect the keyboard language/subtype to drive output language.
- Users who will accept cloud AI only with explicit control over keys, privacy, and apply behavior.

## Outcome and Success Metrics
Target outcome: one-tap cleanup makes dictated text sendable without changing meaning or leaking data unexpectedly.

Success metrics:
- 80%+ of internal dogfood rewrite attempts are accepted or lightly edited afterward.
- Rewrites preserve detected/input keyboard language in 95%+ of test cases.
- No raw text, prompts, or API keys are logged in normal app logs.
- P95 cloud rewrite request completes within 4 seconds for short passages under 1,000 characters on normal mobile connectivity.
- P95 local/on-device proofread probe target is under 2 seconds for short voice-like snippets on supported devices, if the provider is available.
- 0 known unrecoverable editor corruption cases: failed rewrites must leave original text untouched.

## Scope
### In Scope
- A new AI rewrite action reachable from the keyboard, likely from the smartbar/actions row or a long-press/secondary voice-key affordance.
- Text capture policy for recently dictated text, current selected text, and fallback recent text before cursor.
- Grammar/proofreading mode optimized for speech artifacts: remove stutters, repetitions, false starts, and obvious grammar issues while preserving meaning.
- Language selection based on active keyboard subtype, with fallback auto-detect only when active subtype is ambiguous.
- Cloud rewrite provider abstraction, initially compatible with user-provided keys and direct device-to-provider calls or a future relay.
- Secure credential handling modeled after `VoxtralSecretsStore`.
- Preview UI and apply/cancel behavior so users can inspect rewritten text before replacement.
- Undo/recovery behavior, at minimum preserving the original text until replacement succeeds.
- Research and probe for ML Kit GenAI Proofreading/Rewriting and on-device Gemini Nano/AICore feasibility.
- Discovery item for the truncated “doesn't auto...” feature request.

### Out of Scope
- Replacing the existing Voxtral transcription implementation in this project.
- Automatic rewriting immediately after every dictation without explicit user action.
- Full assistant/chat UI inside the keyboard.
- Training or bundling a custom local LLM in the MVP.
- Sending user text to any cloud model without explicit setup and consent.
- Broad predictive typing/autocorrect roadmap work already covered by `typing-speed-core` and `predictive-typing-quality-trust`.

## Functional Requirements
1. Provide a single explicit rewrite action that can be triggered after dictation or after selecting text.
2. Determine the text range to rewrite in this order: selected text, last committed dictation transcript range, bounded recent text before cursor.
3. Never rewrite password, payment, or no-personalized-learning fields; follow existing editor/privacy flags where available.
4. Build a prompt/request that asks for grammar cleanup, removal of speech artifacts, and meaning preservation.
5. Use the active keyboard subtype language as the preferred output language.
6. Support at least EN and NL planning cases; avoid hardcoding English-only prompts.
7. Show a preview before replacing text in the first implementation.
8. Replacement must be transactional from the user's point of view: failure leaves original text unchanged.
9. Provide visible loading, success, cancel, and error states.
10. Store API keys only in encrypted local storage and never in normal preferences/plain logs.
11. Do not log user text, prompt bodies, API responses, or API keys.
12. Allow provider configuration without blocking a future local/on-device provider.
13. Include a lightweight evaluation corpus of voice-like snippets with expected properties rather than exact string-only matching.
14. Capture and resolve the additional truncated “doesn't auto...” request before final scope approval.

## Non-Functional Requirements
- Privacy-first defaults: user text leaves the device only after explicit configuration and action.
- Keep keyboard interaction non-blocking; long network/model calls must run off the main thread.
- Preserve IME stability: editor replacement code must handle apps with poor `InputConnection` behavior.
- Avoid large APK bloat in MVP; downloadable on-device models must be optional/progressive.
- Provider layer must isolate cloud/local implementations from keyboard UI and editor replacement logic.
- API keys must be maskable, clearable, and never exported accidentally through logs or backups if avoidable.
- Language behavior must degrade transparently when a provider does not support the active subtype language.

## Hypotheses and Unknowns
- H1: A preview-first grammar rewrite action will be useful enough even with 2-4 second cloud latency.
- H2: Users prefer explicit rewrite over automatic post-dictation cleanup because it preserves control and trust.
- H3: Active subtype language is a strong enough signal for the rewrite output language in most cases.
- H4: The current editor abstraction can support bounded replacement safely, but selected-text and last-dictation replacement need proof.
- H5: ML Kit Proofreading/Rewriting may eventually be the best local path, but beta status, language coverage, device availability, and style fit need a probe.

Unknowns:
- Exact contents of the second user-requested feature after “it doesn't auto...”.
- Which cloud LLM/provider should be first-class for MVP: Mistral, OpenAI-compatible, Gemini, or reuse the existing Voxtral/Mistral account setup.
- Whether direct device-to-provider calls are acceptable for user-provided keys or a controlled relay is needed for cost/rate/privacy governance.
- Whether Android `InputConnection` can reliably replace the last dictated range across target apps.
- Which on-device GenAI APIs support Dutch well enough for the keyboard-language requirement.

## Touchpoints to Exercise
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/VoxtralDictationManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/VoxtralSecretsStore.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/AbstractEditorInstance.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyData.kt`
- Settings screens for Voxtral/API key configuration and future AI rewrite provider configuration.
- Smartbar/actions UI where the rewrite action should live.

## Probe Findings
Initial research findings:
- OwnKey already has the core dictation insertion point and secure secret storage pattern needed for an MVP.
- ML Kit Proofreading is unusually aligned with this exact use case because it distinguishes `VOICE` input from `KEYBOARD` input.
- ML Kit Rewriting is broader tone/style rewriting; proofreading is likely better for the first “clean up dictated text” action, while rewriting styles can be a later expansion.
- On-device APIs are attractive but cannot be assumed universally available; the delivery plan should treat local inference as a provider probe, not an MVP blocker.
- Replacement safety is a bigger product risk than calling an LLM. A bad replacement UX can destroy trust even if the model output is good.

## Footguns Discovered
- Auto-rewriting every dictation would be risky because it can silently change meaning.
- Rewriting too much context before cursor can accidentally alter unrelated previous messages or notes.
- Cloud provider calls from an IME can leak extremely sensitive text if consent and field filtering are weak.
- If app logs include prompts/responses, the privacy model fails even when credential storage is secure.
- Beta on-device APIs may break backward compatibility and cannot be the only path until probed.
- Language preservation cannot rely on the LLM “figuring it out” if the prompt or provider defaults to English.

## Remaining Unknowns
- Confirm the missing second feature request from the truncated original message.
- Choose first MVP provider and credential UX.
- Confirm target Android/API constraints for the intended build.
- Verify selected-text and last-dictation replacement in representative apps: Mattermost, WhatsApp, Gmail, Obsidian/notes, browser text fields.
- Verify ML Kit language list and Dutch support for Proofreading/Rewriting.

## Dependencies
- Existing Voxtral dictation flow and settings.
- Existing editor content and replacement abstractions.
- Existing keyboard action/smartbar affordance system.
- `typing-speed-core` for overlapping autocorrect/trust controls.
- `predictive-typing-quality-trust` for language and privacy principles.
- User clarification for the truncated second feature.

## Approval Notes
Draft created from Bart's feature request on 2026-05-01 and initial local/web research. Probe required before implementation because editor replacement safety, provider choice, local model availability, and the second requested feature are not yet resolved.
