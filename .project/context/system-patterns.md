# System Patterns

Capture architecture and delivery patterns that should be reused.

1. **User-control-first AI**
   - Every cloud AI feature should expose provider, endpoint/model, and key expectations clearly. Do not hide provider lock-in behind friendly copy.

2. **Privacy claims must match implementation**
   - It is safe to say Ownkey does not add private-content monitoring and stores keys encrypted on-device. Also say that enabled cloud AI sends request data to the configured provider.

3. **AI as umbrella, provider as implementation**
   - Use `AI` for user-facing settings sections that include dictation and rewrite. Keep provider-specific names like Voxtral, OpenAI, Anthropic, and Mistral inside configuration details.

4. **Onboarding/settings visual continuity**
   - New settings surfaces should mirror the Ownkey onboarding direction: dark background, restrained panels, clear sections, brand tokens, and low-noise hierarchy.

5. **Latency-first typing loop**
   - Treat suggestion latency and keyboard responsiveness as hard product constraints. AI features must not make ordinary typing feel slower.

6. **Evidence-backed closure**
   - For implementation tasks, capture test/build evidence and any visual caveats before closing or pushing.

7. **Store copy from real features only**
   - Google Play USPs should reflect shipped or directly implemented behavior. Avoid promising hosted AI, private cloud processing, or model capabilities that the app does not control.

8. **Local inference is a separate, explicit route**
   - Downloaded, selected and running states are independent. Snapshot the backend/model before microphone capture. A missing/crashed local model fails closed and never sends audio to cloud.
   - Keep native inference in the unexported `:offline_asr` service. Local audio/model data lives in `noBackupFilesDir`; process startup skips main-app initialization. Cancel and process death invalidate late results.
   - Internal Orukeet builds use the pinned ASR-only sherpa runtime; the stock AAR contains unused TTS dependencies. Public enablement requires the physical gates in the Orukeet project. Voice rewrite always discloses its separate cloud text step.
