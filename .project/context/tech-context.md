# Tech Context

## Stack
- Android app modules (`app`, `wear`) with Kotlin + Gradle
- FlorisBoard-derived IME architecture
- Local dictionaries, suggestion ranking, and autocorrect pipeline in keyboard engine

## Runtime Constraints
- Suggestion pipeline must keep p95 latency under 50 ms for top-3 prediction updates.
- Keyboard open and first input interaction should feel instant (<200 ms target) on representative devices.
- Privacy-first behavior: avoid introducing private-content telemetry; rely on aggregate/performance-safe metrics.

## Integration Points
- Existing suggestion/autocorrect components in the app module
- User dictionary and per-language lexicon storage
- Settings UI for behavior tuning (conservative/balanced/aggressive)
- Optional app-context detection hooks for chat vs mail behavior profiles
