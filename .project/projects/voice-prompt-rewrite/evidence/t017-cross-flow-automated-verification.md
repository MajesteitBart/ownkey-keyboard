# T-017 Cross-Flow Automated Verification

Date: 2026-08-05
Risk level: high

## Scope and test seam

The app keeps Compose as a state renderer over `VoiceRewriteUiController` and immutable
`VoiceRewriteUiModel` values. Cross-flow automation therefore exercises the production gesture
arbiter, target resolver, availability policy, audio-session coordinator, transcription-only
operation, voice-rewrite session manager, UI controller, target verification, and replacement
gateway without starting Android UI or making a network request.

`VoiceRewriteCrossFlowTest` adds one reusable deterministic fixture family for editor frames and
Select All behavior, recorder ownership and cleanup, transcription/rewrite delay or failure,
disclosure persistence, routes, replacement/copy behavior, and virtual time.

## Cross-flow scenarios

- Platform-timed long press dispatches voice rewrite once; release is inert. The flow then resolves
  the target, presents first-use provider disclosure, records, pauses/resumes, transcribes, rewrites,
  reviews, replaces only after explicit intent, publishes success, and returns to idle.
- Asynchronous Select All remains in `TARGETING` and starts no recorder/provider work until a visible
  full-field selection update is confirmed.
- Selection mutation after review blocks replacement and exposes copy fallback while preserving host
  text.
- Field invalidation during delayed transcription cancels the session; a stale non-cooperative
  completion cannot dispatch rewrite or mutate the new field, and temporary recorder data is absent.
- A rewrite-provider failure preserves the target/instruction for deterministic retry without a
  second recording or transcription request.

## Existing component and regression coverage used by the gate

- `VoiceRewriteSessionPreflightTest` covers every ordered prerequisite, secure/incognito blocking,
  busy recorder behavior, lifecycle invalidation, and stale generations.
- `VoiceRewriteTargetResolverTest`, `VoiceRewriteReplacementTest`, and
  `VoiceRewriteContractProbeTest` cover synchronous/asynchronous Select All, native/Compose/messaging/
  WebView/raw/secure/problematic editor profiles, empty/over-limit targets, and field/package/session/
  range/source/integrity drift.
- `AudioSessionCoordinatorTest`, `TranscriptionOperationsTest`, `VoiceRewritePipelineTest`, and
  `VoiceActionFeedbackControllerTest` cover single-recorder ownership, one transcript commit,
  provider failures, cancellation, 900 ms success, five-second error reset, immediate retry, newer
  outcome replacement, and disposal with controllable coroutine time.
- `AudioLevelHistoryReducerTest` and `AudioLevelHistorySamplerTest` cover measured-only input,
  silence/quiet/normal speech distinction, bounded history, pause, saturation, reduced motion, and
  absence of clock-only activity.
- `VoiceActionGestureArbiterTest`, `RewritePromptPresetsRegressionTest`, typing/suggestion suites, and
  transcription-language tests preserve tap-to-dictate dispatch, non-voice quick actions, preset
  identity/order, typing-critical behavior, the fixed source-language policy, and the absence of any
  locale/subtype output-language decision.

## Commands and results

- Targeted cross-flow command: `gradlew.bat :app:testDebugUnitTest --tests
  "dev.patrickgold.florisboard.ime.voice.VoiceRewriteCrossFlowTest" --no-daemon --console=plain`
  - 5 tests passed, 0 failed.
- Offline-safe app JVM command: `gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`
  - 271 tests across 39 suites passed, 0 failed, 0 skipped.
  - A second invocation completed successfully with every task up to date, confirming the suite has
    no network or wall-clock dependency.

No real user content, credentials, endpoints, provider bodies, raw prompts, or absolute paths are
present in the fixtures or this evidence.

## Coverage carried forward

Rendered device/editor/TalkBack behavior, real-microphone calibration, provider-dependent language
behavior, and screenshots/recordings remain release-gated by T-018. Performance, privacy/logging,
temporary-file recovery, rollout, and rollback review remain release-gated by T-019.
