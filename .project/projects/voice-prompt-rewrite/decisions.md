---
name: Voice-Prompt Rewrite
slug: voice-prompt-rewrite
owner: ownkey-keyboard-team
created: 2026-08-04T08:33:57Z
updated: 2026-09-09T07:48:24Z
---

# Decisions: Voice-Prompt Rewrite

## Active Decisions

### D-001: Long-press the dictation key for voice rewrite

- **Status:** accepted
- **Date:** 2026-08-04
- **Decision:** A normal dictation-key tap remains ordinary dictation. A platform-timed long-press enters voice rewrite, provides haptic and visible `Speak your instruction` feedback, and continues recording after finger release.
- **Rationale:** This brings the Windows shortcut's immediacy to Android without adding another permanent smartbar control or changing the familiar tap action. Android's configured long-press timing is used instead of a fixed three-second delay for responsiveness and accessibility.

### D-002: Resolve a missing selection through visible Select All

- **Status:** accepted
- **Date:** 2026-08-04
- **Decision:** Existing selected text is the rewrite target. When no selection exists, Ownkey visibly invokes Select All, waits for the editor to confirm a non-empty selection, labels it as `Whole field`, and only then records or sends data.
- **Rationale:** Visible selection makes scope understandable and avoids silently inferring incomplete field content from Android's bounded surrounding-text APIs. Unsupported, empty, secure, or over-limit targets fail before microphone/network use.

### D-003: Keep a visible and accessible alternate entry

- **Status:** accepted
- **Date:** 2026-08-04
- **Decision:** The pinned `Tell Ownkey what to change` action remains in the Rewrite panel, and the dictation key exposes a `Voice rewrite` accessibility action plus a one-time `Tap to dictate · hold to rewrite` coach mark.
- **Rationale:** Long-press is efficient but hidden. The visible card and accessibility action preserve discoverability and full TalkBack operation.

### D-004: Preserve review-before-replace

- **Status:** accepted
- **Date:** 2026-08-04
- **Decision:** Both shortcut and visible entry converge on the same result preview and explicit `Replace` action.
- **Rationale:** Spoken-instruction transcription adds uncertainty; preview protects source text and matches Ownkey Android's existing trust model.

### D-005: Separate the recording action from live audio feedback

- **Status:** accepted
- **Date:** 2026-08-04
- **Decision:** Ordinary dictation and voice rewrite share a first-action-row recording composition: elapsed time, a centered microphone-amplitude waveform, pause/resume and cancel immediately to its right, and an orange stop-square control in the normal dictation-key position. The action button does not contain waveform bars while recording.
- **Rationale:** A control should communicate what tapping it will do, while a waveform should communicate what the microphone is receiving. The visible mic pill's current sine animation looks active regardless of speech; the repository already has an unwired row composition and live amplitude flow that support the clearer hierarchy.

### D-006: Make terminal mic-button feedback explicit and transient

- **Status:** accepted
- **Date:** 2026-08-04
- **Decision:** Retain the existing idle, triggered/recording, processing, success, and error visual treatments, but drive success/error from explicit outcomes. Success shows for approximately 900 ms. Error shows for approximately five seconds, remains immediately retryable, announces once, and then returns to idle automatically. Persistent recovery copy, when needed, lives outside the button.
- **Rationale:** The current error state can remain indefinitely and make the mic appear permanently unavailable. A bounded acknowledgement communicates the event without turning a past failure into the apparent current state; explicit outcomes also prevent false success after a non-success processing-to-idle transition.

### D-007: Incognito mode disables every cloud AI action

- **Status:** accepted
- **Date:** 2026-08-04
- **Decision:** While the active editor session is in incognito mode, Ownkey disables all configured-provider AI actions: dictation/transcription, preset rewrite, and voice rewrite. The affected controls remain visible but disabled and state a specific reason plus a route to leave incognito or open the relevant setting; they are never silently inert. Incognito is evaluated per editor session alongside the secure-field check, before target resolution, microphone access, or any provider request.
- **Rationale:** Incognito currently governs only on-device learning, but users read it as a private mode, and dynamic incognito is switched on by the host app through `flagNoPersonalizedLearning` — which is exactly the context (password managers, banking, sensitive messaging) where audio and selected text must not leave the device. Gating transcription alone would leave preset rewrite as an equivalent path for selected text to reach a provider, so one rule covering all cloud AI is both safer and easier to explain, test, and market.
- **Consequences:**
  - This is a deliberate, user-visible change to ordinary dictation and preset rewrite availability, not only to the new voice flow.
  - The incognito toast copy currently promises only that Ownkey "will not learn words from your input"; it must be updated to state the AI gate.
  - `IncognitoMode.FORCE_ON` permanently disables AI for that user, so the Typing settings entry needs a warning.
  - Incognito is not equivalent to a secure field: secure fields also prevent content from being read at all, while incognito is a session-scoped availability policy.

### D-008: Rewrite output stays in the source text's language unless the instruction says otherwise

- **Status:** accepted
- **Date:** 2026-08-04
- **Decision:** A voice rewrite returns text in the same language as the captured source text. An explicit language request in the spoken instruction — translate this to French, answer in Dutch — overrides that default. The language the instruction is *spoken* in never determines the output language on its own: a Dutch instruction against English text returns English. Mixed-language source text is preserved rather than normalized to one language.
- **Rationale:** The common case is editing text in place, where a silent language switch would be destructive and surprising. Translation is a legitimate but explicit intent, so it must be requested rather than inferred from the instruction's own language. Multilingual users were already a named primary user of this feature, and speaking an instruction in one's stronger language against text in another is precisely the case the voice path enables.
- **Consequences:**
  - The rule lives in the app's fixed rewrite policy sent alongside the content, so the provider model resolves the source language. Ownkey does not add local language detection, and the rule stays separate from user content per FR-022.
  - Voice-rewrite instruction transcription always sends no language hint and relies on provider auto-detection, regardless of the configured dictation language behavior. This keeps cross-language instructions possible and is validated separately from ordinary dictation's subtype/explicit/Auto modes.
  - Output-language behavior depends on provider models and cannot be asserted deterministically in unit tests. The testable contract is that the policy carries the rule and that instruction language is never used locally to select an output language; real behavior is verified in the device and provider matrix.

### D-010: Show the recognition language on the spacebar during dictation

- **Status:** accepted
- **Date:** 2026-08-04
- **Decision:** While a dictation session is active, the spacebar — the surface that already names the active language — indicates the language speech is being recognised in. Voice rewrite shows no such cue, because its instruction transcription sends no language hint.
- **Rationale:** D-009 makes the keyboard subtype the dictation default, which is otherwise invisible. Marking the surface that already carries the language name answers "which language am I being heard in" without new chrome, and the absence of the cue in voice rewrite is itself accurate information: nothing is pinned there.
- **Consequences:**
  - The language must be readable as text while the cue is active, not conveyed by highlight or colour alone, so `SpaceBarMode` values of `NOTHING` and `SPACE_BAR_KEY` need a defined behavior rather than an unlabelled glow.
  - The cue is scoped to the dictation session, so it does not depend on the Rewrite hub, which does not compose the spacebar.
  - Exact treatment and timing within the session are a T-001 probe recommendation rather than a fixed visual contract.

### D-009: Transcription language defaults to the active keyboard language

- **Status:** accepted
- **Date:** 2026-08-04
- **Decision:** The two speech paths resolve language differently, because they do different jobs.
  - **Ordinary dictation** resolves in this order: an explicit language set by the user wins; otherwise the active keyboard subtype's primary language is sent; an explicit `Auto` choice sends no hint. This replaces the current behavior where an unset hint sends nothing.
  - **Voice-rewrite instruction transcription** sends no language hint at all and always relies on provider auto-detection.
- **Rationale:** Dictation produces text that becomes the user's writing, so it is almost always in the keyboard's language, and the subtype is a strong, already-explicit signal. A rewrite instruction is a command that is never inserted; its language is independent of the text being edited, and multilingual users specifically want to issue it in whichever language is natural. Pinning the instruction to the keyboard language would penalise exactly the case the voice path exists to enable. Refined on 2026-08-04 after review; the initial form applied one rule to both paths.
- **Consequences:**
  - `Auto` must remain reachable as an explicit choice. Today an empty field means auto; once empty means keyboard language, auto disappears unless the setting offers it deliberately. The AI settings field changes from free text with an implicit empty state to three reachable states.
  - This changes transcription request construction for existing users, so it is a deliberate change to ordinary dictation behavior and is carved out of the project's out-of-scope line alongside the incognito gate.
  - Splitting the two paths removes the cross-language tension with D-008: an English keyboard with a Dutch instruction no longer sends an English hint, because the instruction path sends none.
  - The cost moves to the monolingual case. A user who always speaks instructions in their keyboard language loses the accuracy a correct hint would have given, and auto-detection is weakest on short utterances — which instructions usually are. T-018 measures this before release; if it proves unacceptable, the fallback is to send the subtype hint for instructions too and accept the cross-language cost instead.
  - Because the hint applies only to dictation, the recording-time language cue is needed only there. The Rewrite hub does not need one, which resolves the fact that the spacebar is not composed while that panel is open.
  - This governs only what is heard, never what is written. Output language still follows D-008: the source text's language unless the instruction requests otherwise. The keyboard language never selects the output language. The hint is sent only as the transcription request's `language` field and never reaches the rewrite client.
  - `WearVoxtralSync` carries `languageHint` to the Wear companion. Wear support is out of scope here, but redefining an empty value from "auto" to "subtype language" changes what that payload means, so the resolution must happen where the transcription request is built rather than by reinterpreting the stored value.

### D-011: Defer bespoke persistent undo from the first release

- **Status:** accepted
- **Date:** 2026-08-04
- **Decision:** The first voice-rewrite release does not add a bespoke persistent `Undo rewrite` chip, result history, target history, or cross-session recovery state. Result preview remains mandatory before `Replace`; replacement still requires live target revalidation and falls back to `Copy result` when unsafe. After a successful replacement, recovery uses the host editor or keyboard's existing undo behavior.
- **Rationale:** Preview-before-replace and stale-target safeguards prevent the highest-risk accidental mutation before it happens. A separate persistent undo system would retain sensitive text and editor state, add lifecycle and migration complexity, and duplicate host recovery behavior without being required for the first delivery.
- **Consequences:**
  - No voice-rewrite result, instruction, target, or undo payload is persisted for later recovery.
  - Before replacement, the current valid session may retain the generated result and recognized instruction in memory for retry or copy fallback.
  - After replacement, Ownkey shows the transient success state, closes the panel, and creates no durable undo surface or storage.
  - Rollback requires no undo-state cleanup or migration. A future bespoke undo proposal requires a new privacy, lifecycle, and editor-compatibility decision.

### D-012: Accept simulated-device evidence for the M0 architecture gate

- **Status:** accepted
- **Date:** 2026-08-04
- **Decision:** Emulator interaction evidence plus deterministic simulated editor, accessibility-timing, signal, lifecycle, and layout matrices are sufficient to close T-001 and activate implementation. This acceptance applies only to the M0 contract/architecture gate; T-018 retains representative physical-device, real-microphone, rendered-layout, editor, and TalkBack validation before release.
- **Rationale:** The executable models retire the design uncertainties needed to choose production seams, while the API 35 emulator demonstrates the live InputConnection behavior available now. Keeping physical validation as an explicit release gate avoids blocking implementation on device access without converting simulation into a compatibility claim.
- **Consequences:**
  - Probe artifacts distinguish observed emulator behavior from simulated contract coverage.
  - T-018 failures block release and require repair or reopening of the affected contract.
  - No physical-device, microphone-calibration, or TalkBack claim is made by closing T-001.

### D-013: Voice rewrite recording lives in the rewrite panel

- **Status:** accepted
- **Date:** 2026-09-08
- **Decision:** Ordinary dictation keeps the smartbar recording row and is its only owner. Voice rewrite presents recording, pause, processing, review, recovery, and success as one state-driven body inside the AI rewrite panel, which is always open while a session runs. While the panel is open the smartbar hides suggestions and the voice key and shows one labelled close control. The measured-amplitude waveform, recorder arbitration, and the 30-second cap are shared; only the presentation moved.
- **Rationale:** The Samsung Notes demonstration showed a result sheet competing with a dimmed hub, the same rewrite status rendered in the smartbar row and in the panel, and up to three spinners at once. One visible owner per workflow makes it obvious whether the user is dictating text or recording an instruction, and removes the routing branch that decided per control which mode owned the row.
- **Consequences:**
  - Refines D-005: the shared row composition now applies to ordinary dictation only, and voice rewrite reuses the presentational waveform rather than the row. FR-042 in the spec is superseded on the placement of rewrite controls; their meaning and 48 dp targets are unchanged.
  - Cancel, Stop, Pause, Resume, Try again, navigation, and Close use neutral surfaces; Stop stays prominent through a high-contrast fill and its stop-square glyph. The filled accent is reserved for the committing action of a state and the replaced confirmation.
  - The in-panel `Text replaced` and `Inserted` confirmations dwell about 1.2 seconds (the plan's figure; previously 900 ms in the spec's replacement flow) and the timer is bound to that confirmation, so it cannot close a newer session. D-006 is unchanged: the mic button's own green success acknowledgement for ordinary dictation still lasts about 900 ms.
  - The smartbar close control cancels an active voice-rewrite session and releases the recorder explicitly before hiding the panel, in addition to the panel's disposal safeguard.
  - Dictation shows one processing spinner; the key slot holds Cancel while transcribing. The dictation start toast remains only for keyboards whose smartbar is switched off.

### D-014: Waveform reads as a waveform and the dictation key answers every press

- **Status:** accepted
- **Date:** 2026-09-09
- **Decision:** The measured-level meter keeps its 18-sample, 20 Hz, noise-floored history from T-001 but smooths it far less (attack 0.9, release 0.55, exponent 0.75 instead of a square root), draws bars on a fixed 3 dp / 6 dp grid, and never grows past 108 dp; the row centres its whole control cluster around it. The dictation key gives a platform press haptic on touch, grows slightly, fills an accent ring over the platform hold timeout, gives a distinct long-press haptic when the hold becomes voice rewrite, and shows a short `Hold to rewrite` bubble above itself while pressed and for about 1.4 s after a tap. Both haptics follow the system touch-feedback setting rather than the keyboard's typing-vibration preferences.
- **Rationale:** On a phone and on an unfolded foldable the previous meter stretched across the free width, and its 200 ms release turned each syllable into a hump travelling along the row, so it read as a stretched blob rather than audio. The key gave no felt or seen response on devices where keyboard haptics are off (the default) and taught the hold only once, through the coach mark.
- **Consequences:**
  - Refines T-007's calibration and the T-001 width policy; every bar is still a measured sample and silence still settles to the baseline stubs. Reduced motion keeps the stationary 5 Hz level and gets no ring animation.
  - The hint is drawn by the IME root from the key's reported bounds, inside the IME window and outside its touchable region, so it needs no popup window and cannot intercept host-app touches. It is absent from the accessibility tree; TalkBack keeps the explicit voice rewrite action.
  - The generic long-press tooltip is disabled on the voice key only; a hold there opens voice rewrite.
  - An unavailable key (incognito, secure field, busy microphone) still answers with its reason and shows neither the hint nor the ring.

### D-015: The meter is the Ownkey mark breathing in place, not a scrolling history

- **Status:** accepted
- **Date:** 2026-09-09
- **Decision:** The dictation row and the rewrite sheet draw nine stationary bars in the silhouette of the Ownkey waveform mark (the same centre-peaked, asymmetric profile the Windows overlay uses). Each bar keeps a fixed slot and a fixed lag into the reducer's 18-sample history, and its height is its share of the mark scaled by the level measured that many samples ago. The row shows the mark at 4 dp bars on a 7 dp pitch (63 dp wide); a cramped row drops outer bars down to five. Silence is a flat row of minimum stubs; the silhouette exists only while someone speaks. The level itself is scaled against a rolling peak of the measured input (target 0.92, floor 0.2, decay 0.994 per sample), as the Windows overlay does, so the loudest recent speech fills the meter whatever gain the phone's microphone chain applies. Level state is read only in the canvas draw pass.
- **Rationale:** The product owner reviewed the scrolling history on device: a good meter has static bars that grow and shrink, and the scrolling strip read as a shape crawling sideways. The Windows overlay already presents the mark this way. Before changing the meter the earlier battery findings were re-read: an amplitude poll that ran while idle (May 2026), a looping sine animation on the mic key (July 2026), a poller restarted by every level publication and per-sample settings reads (August 2026 reviews), and the clipboard wake-up (T-021). The stationary mapping is a pure function of the existing sampler state, adds no clock, and removes a per-sample recomposition of the whole smartbar, so it reintroduces none of them.
- **Consequences:**
  - Supersedes D-014's bar grid, bar count, and 108 dp cap; D-014's smoothing constants and key feedback stand. The spec's audio-reactive waveform contract is reconciled on the same date.
  - Every bar is still a measured recent sample; there is no clock-driven, random, or prerecorded motion, and the sampler still runs only while recording. Reduced motion keeps the stationary 5 Hz level, which now breathes the whole mark evenly.
  - The rolling peak is itself only ever a measured value, is gated by the noise floor before scaling, and never drops below its floor, so silence divides to exactly zero and a whisper cannot be amplified into a shout. On-device review showed a shout arriving at roughly a fifth of full scale on a Samsung phone and a resting silhouette that read as stuck while silent; both are what this scaling and the flat baseline correct.
  - Neighbouring slots read samples at least two apart, so adjacent bars never rise together and no ripple travels outward from the centre.
  - The unused Windows idle animations (breathe, bounce, ripple) are deliberately not ported: they are CSS keyframe loops, which is exactly the class of motion the Android meter must not run.

## Superseded Decisions
- None.

## Open Decision Questions

- None.
