# Ownkey Android Brand Token Map

Source reference: `docs/brandbook/ownkey-delano-brand-reference-2026-05-31.html`

Canonical assets:
- App icon/tile: `assets/branding/ownkey-app-icon-keycap.svg`
- Compact mark: `assets/branding/ownkey-monkey-waveform-mark.svg`

## Color Roles

- `OwnkeyBrand.Key` / `#0A0B0C`: deepest app and icon base.
- `OwnkeyBrand.Graphite` / `#18191B`: primary keyboard and app dark surface.
- `OwnkeyBrand.Panel` / `#111315`: settings/setup panels and quiet IME panels.
- `OwnkeyBrand.PanelRaised` / `#1C1D20`: selected cards, recording panels, and elevated controls.
- `OwnkeyBrand.Action` / `#25272D`: inactive controls and secondary action wells.
- `OwnkeyBrand.ActionPressed` / `#30333A`: completed or pressed action states.
- `OwnkeyBrand.Line` / `#2B3037`: low-contrast dividers and borders.
- `OwnkeyBrand.Bone` / `#F3F1EC`: primary text and monkey face color on dark surfaces.
- `OwnkeyBrand.Ash` / `#B6BAC3`: secondary labels, helper text, and muted metadata.
- `OwnkeyBrand.SignalOrange` / `#F56C1E`: voice, AI, active recording, primary keyboard actions, and brand signal moments.
- `OwnkeyBrand.Ember` / `#DE5F14`: the logo orange. The dictation button, the recording level, the enter key in the Signal themes, the default accent, and an AI result that is ready.
- `OwnkeyBrand.Coal` / `#161616`: the well of a quiet dictation button while idle, transcribing, or failed.
- `OwnkeyBrand.Stone` / `#8E8A7F`: toolbar icons on dark surfaces, including the AI action while it is idle.
- `OwnkeyBrand.StoneDark` / `#5E5A52`: toolbar icons on light surfaces such as Signal Bone, where Stone falls below 3:1 contrast.
- `OwnkeyBrand.SignalAmber` / `#F5A524`: warning and paused voice states.
- `OwnkeyBrand.TrustBlue` / `#2F6BFF`: primary setup/settings actions.
- `OwnkeyBrand.SuccessGreen` / `#3EDB83`: success feedback only.
- `OwnkeyBrand.ErrorRed` / `#FF7A7A`: error feedback only.

## Typography

- Use Android system typography through Material/Compose rather than bundling web fonts from the brand book.
- Setup/settings headings use `titleLarge` or `titleMedium` with semibold weight.
- IME labels stay compact: 12-13sp for status, timer, toast, and dense smartbar copy.
- Avoid hero-scale text inside app/settings cards and keyboard surfaces.

## Shape

- App/setup panels: 18-22dp rounded corners.
- IME controls: circles for icon-only controls, 16-18dp rounded panels for transient banners.
- App icon: dark keycap tile with the monkey waveform mark centered inside the adaptive icon safe area.

## Motion

- Fast state feedback: 120ms.
- Dictation panel entry/exit: 180ms slide/fade.
- Audio meter updates: 80ms so the meter feels live without making layout shift.
- Motion must never delay typing or insertion.

## Themes and icons

- The default keyboard theme is Ownkey Signal Graphite (`#111111` background, `#1A1A1A` keys). Signal Black is the OLED variant and Signal Bone the day theme. They ship as the built-in extension `ai.bvdm.ownkey.themes.signal` and appear as the first two theme presets.
- Keyboard icons are Heroicons Mini (20px solid, MIT). Keyboard and waveform glyphs, which Heroicons lacks, are drawn to match it.

## Dictation button states

One face is shared by the mic key, the recording row, and the voice-only bar (`MicButtonFace`).

- Idle, solid: Ember circle with a white microphone. Used in the Smartbar and the voice-only bar.
- Idle, quiet: Coal circle with an Ember microphone. Used in the floating split strip, where the mic sits among other actions.
- Listening: Ember circle, a white ring at 45% opacity, and a white stop square.
- Paused: Ember circle at 55% opacity with a white stop square. Tapping it stops and transcribes.
- Transcribing: Coal circle with a faint ring and a quarter Ember arc that spins. No icon.
- Inserted: green circle with a white check, shown briefly after the transcript lands.
- Error: Coal circle with a Bone exclamation circle. The voice-only bar names the reason, such as "No API key" or "No microphone access".
- Unavailable: Coal circle with a Stone no-symbol, in fields where AI is off, such as password or incognito fields.

## IME Constraints

- Orange is reserved for voice, AI, and active states inside the keyboard, plus the primary action: the enter key and the accent color.
- Toasts must render inside the IME host while typing in other apps and fall back to Android toasts only outside the IME.
- Keyboard controls must preserve stable height, hit areas, and alignment across compact widths.
- Branding must not add decorative content that competes with keys or candidate/action rows.
