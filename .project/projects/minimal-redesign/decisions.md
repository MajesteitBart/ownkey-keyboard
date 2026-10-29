---
name: Ownkey Minimal Redesign Decisions
status: active
created: 2026-10-29T03:00:52Z
updated: 2026-10-29T03:00:52Z
---

# Decisions: Ownkey Minimal Redesign

## Accepted
- The settings app is dark only. `other__settings_theme` and `other__accent_color` stay declared but nothing reads them.
- One accent: `theme__accent_color` (default Ember `#DE5F14`) drives the settings app and the keyboard. Glyphs on the accent are white when that reaches 3:1, else black.
- Primary actions are white pills with black text. Enter on the keyboard is a neutral key; the mic is the only accent element.
- Bundle Inter 4.1 for the settings app only. The keyboard keeps the system font so theme loads and key rendering don't change.
- Grouped cards use 2dp black gaps between rows instead of hairlines.
- Hidden settings keep their declarations. A hidden row stays visible while it holds a non-default value, so users can reset it.
- Lock with migrate rules: smartbar layout (any value but extended), on-device vocabulary hints, show app icon, toolbar icon colour/size/style; FOLLOW_TIME becomes FOLLOW_SYSTEM.
- Do not change `OwnkeyDefaultTopBarActions`; new defaults only: toolbar collapsed, three-word suggestions.
- "Rewrite styles" replaces "rewrite voices" for the preset buttons; "Voice rewrite" keeps meaning the spoken-instruction feature.
- Signal stylesheets keep their ids. Graphite becomes a clearly grey theme so Graphite and Black look different.
- Devtools is reachable only from About (seven taps on the version, or debug builds).

## Pending
- None.
