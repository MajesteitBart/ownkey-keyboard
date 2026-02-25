# Project Structure

Document major repository boundaries and ownership.

- `app/`: Main Android IME application (prediction, autocorrect, settings, UI behavior)
- `wear/`: Wear OS companion IME and dictation entry points
- `lib/compose/`: Shared Compose/UI support components
- `.project/context/`: Canonical cross-agent execution context
- `.project/projects/<slug>/`: Delivery project contracts (spec, plan, workstreams, tasks, updates)
- `.project/registry/`: External mapping and migration registries
