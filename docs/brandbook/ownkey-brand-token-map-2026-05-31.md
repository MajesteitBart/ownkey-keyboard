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
- `OwnkeyBrand.SignalOrange` / `#DE5F14`: voice, AI, active recording, and brand signal moments.
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

## IME Constraints

- Orange is reserved for voice/AI/active states inside the keyboard.
- Toasts must render inside the IME host while typing in other apps and fall back to Android toasts only outside the IME.
- Keyboard controls must preserve stable height, hit areas, and alignment across compact widths.
- Branding must not add decorative content that competes with keys or candidate/action rows.
