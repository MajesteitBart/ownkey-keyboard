---
type: research_findings
project: orukeet-offline-dictation
slug: android-integration
created: 2026-09-15T10:30:31Z
updated: 2026-09-15T12:26:20Z
---

# Findings: Optional Orukeet Android integration

Historical planning research. [Review revisions](../review-revisions/findings.md) supersede the archive-on-phone delivery, unresolved hosting/runtime metadata, and recorder-handoff assumptions below. The current spec and plan contain the revised decisions.

## Source References

Inspected on 2026-09-15; repository baseline `12fa074c`.

- [Audio format](../../../../../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/AudioRecorder.kt)
- [Routing and readiness](../../../../../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/VoxtralDictationManager.kt)
- [Transcription interface](../../../../../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/TranscriptionClient.kt)
- [Transcription and insertion operations](../../../../../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/TranscriptionOperations.kt)
- [Audio session ownership](../../../../../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/AudioSessionCoordinator.kt)
- [Voice rewrite wiring](../../../../../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/rewrite/VoiceRewriteWiring.kt)
- [Privacy gates](../../../../../app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/rewrite/CloudAiAvailabilityPolicy.kt)
- [AI settings](../../../../../app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/voxtral/VoxtralScreen.kt)
- [Preferences](../../../../../app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt)
- [Application startup](../../../../../app/src/main/kotlin/dev/patrickgold/florisboard/FlorisApplication.kt)
- [Extension types](../../../../../app/src/main/kotlin/dev/patrickgold/florisboard/lib/ext/ExtensionManager.kt)
- [Manual backups](../../../../../app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupScreen.kt)
- [Android backups](../../../../../app/src/main/res/xml/backup_rules.xml)
- [Transfer/backup rules](../../../../../app/src/main/res/xml-v31/backup_rules.xml)
- [Existing voice contract](../../../voice-prompt-rewrite/spec.md)
- [Gradle configuration](../../../../../gradle.properties)

External primary sources are linked beside the associated architecture decisions in [the plan](../../plan.md): upstream model/export/downloader, pinned Hugging Face manifest, sherpa Kotlin API, Android transfer/page-size guidance, and Play executable-code policy.

## Observations

- The referenced model performs speech recognition. The proposed feature is local dictation, not speech synthesis.
- Ownkey already separates returned transcripts from editor insertion and coordinates the microphone across dictation and voice rewrite.
- Capture is AAC in M4A. Local inference needs decoded waveform samples, not the recorder's compressed bytes.
- Dictation currently resolves from API-key presence, with debug mock and release external-IME behavior. Explicit local selection needs a new routing branch and fail-closed readiness.
- Voice rewrite resolves its recorder, mode, and client separately. A unified session snapshot prevents route changes between those resolutions.
- Secure/incognito policy gates existing AI flows. Keep those restrictions for v1; local incognito support is outside this request's implementation plan.
- Application startup clears cache and initializes preferences, clipboard, dictionaries, and extensions. Models/partials belong outside that cache, and an inference process needs minimal startup.
- The extension manager indexes keyboard, theme, and language-pack ZIPs. A versioned local-model manager is a better fit for the pinned large archive.
- The pinned manifest has archive size 486,807,585 bytes and seven extracted files totaling 671,619,800 bytes. Python retrieval independently matched its SHA-256 to the upstream downloader's expected hash. No archive or model inference was downloaded/run.
- Current upstream source describes newer export tooling as well as a previously optimized release. The plan pins the downloader's exact artifact instead of assuming every export with the same filename is identical.

## Options Considered

| Option | Benefit | Cost | Recommendation |
| --- | --- | --- | --- |
| Bundle model weights | Available immediately | Large install for every user | Exclude: user requires optional download. |
| Bundle lazy native runtime; download model data | Same delivery for Play and standalone APK | Some base-app runtime overhead | Initial design; measure overhead. |
| Play on-demand runtime module | Smaller base app | Separate Play/standalone paths | Revisit only if measured overhead requires it. |
| Native decode in IME process | Less IPC | Native crash/memory pressure affects keyboard | Prefer a dedicated process. |
| Direct PCM recording | Avoids codec decode | Changes capture/pause/waveform behavior | Compare only if existing M4A path misses targets. |
| Streaming phrase stitching | Partial text sooner | VAD, overlap, reconciliation, longer sessions | Defer; first release processes bounded phrases. |

## Fold-Forward Candidates

Completed: product behavior in `spec.md`, architecture and delivery gates in `plan.md`, recommendations in `decisions.md`, and planning/validation evidence in the update note. All remain planned; no implementation is activated.

## Open Questions

Device measurements must settle the runtime build, minimum supported tier, duration cap, cold/warm latency, native memory, cancellation, and sustained heat/battery behavior. Hosting/range behavior and final distribution notices also need implementation verification. These do not prevent delivery of the requested plan.
