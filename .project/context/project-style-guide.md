# Project Style Guide

## Naming
- Use kebab-case slugs for delivery projects (`typing-speed-core`).
- Use stable task IDs in the form `T-###`.
- Keep workstream names prefixed (`WS-`) and outcome-oriented.
- Use `Ownkey` as one word in product copy.
- Use `AI` as the user-facing umbrella for dictation and rewrite settings.
- Avoid `Voxtral` as a top-level user-facing section name; use it for the Mistral ASR provider/default.

## Documentation Conventions
- Keep all delivery contracts in English.
- Use measurable language in requirements (latency budgets, ratios, percent change).
- Capture dependencies, risks, and acceptance criteria explicitly in each task.
- For Play Store copy, keep claims short, concrete, and implementation-backed.
- Do not overuse abstract privacy language. Name the actual behavior: BYOK, encrypted local key storage, configurable providers, no added in-app monitoring.

## Review Expectations
- Validate KPI alignment before approving scope changes.
- Require evidence entries for tests/benchmarks when moving tasks to `done`.
- Confirm no privacy model regressions when adding metrics or adaptive behavior.
- Confirm Play Store metadata remains understandable to non-technical users even when mentioning providers or endpoints.
- Before final store copy, review current Google Play policy-sensitive claims around AI, privacy, and data handling.
