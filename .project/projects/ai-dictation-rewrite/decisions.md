# Decisions

## 2026-05-01 - Create a separate Delano project for AI dictation rewrite

Decision: Track LLM-powered post-dictation cleanup as its own Delano project, separate from existing predictive typing projects.

Rationale: The feature touches dictation, cloud/local AI providers, secure key handling, editor replacement, and keyboard UX. It has different risks than low-latency next-word prediction or autocorrect confidence tuning.

## 2026-05-01 - Start preview-first, not automatic rewrite

Decision: The first implementation should require an explicit user action and show a preview before replacing text.

Rationale: Dictated text can be personal or sensitive, and LLM rewrites can change meaning. Trust is more important than saving one tap in the first version.

## 2026-05-01 - Treat on-device AI as a provider probe, not an MVP blocker

Decision: Research ML Kit Proofreading/Rewriting and Gemini Nano/AICore, but do not block the first planning path on local inference availability.

Rationale: Local inference is attractive for privacy, but the APIs are beta/availability-dependent. The architecture should support it without betting the MVP on it.
