# Project Structure

Document major repository boundaries and ownership.

- `app/`: Main Android IME application, including keyboard UI, settings, setup/onboarding, dictation, rewrite, prediction, and autocorrect behavior.
- `wear/`: Wear OS companion IME and dictation entry points.
- `lib/compose/`: Shared Compose/UI support components.
- `lib/android/`: Shared Android helpers, including Ownkey toast routing.
- `assets/branding/`: Ownkey source brand assets and generated store graphics.
- `docs/brandbook/`: Ownkey brand reference and token documentation.
- `fastlane/metadata/android/`: Google Play metadata, images, descriptions, and changelogs.
- `.project/context/`: Canonical cross-agent execution context
- `.project/projects/<slug>/`: Delivery project contracts (spec, plan, workstreams, tasks, updates)
- `.project/registry/`: External mapping and migration registries

## High-Value Code Areas
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/voxtral/VoxtralScreen.kt`: current AI settings screen. Internal path/name remains `voxtral`, user-facing section title is `AI`.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/rewrite/`: LLM rewrite implementation and provider presets.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/`: voice dictation flow.
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/OwnkeyBrand.kt`: app-level brand color and motion tokens.
- `app/src/main/res/values/strings.xml`: English user-facing settings and Play-relevant app copy.
