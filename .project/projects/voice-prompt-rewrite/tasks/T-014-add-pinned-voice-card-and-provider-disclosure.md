---
id: T-014
name: Add pinned voice card and provider disclosure
status: done
workstream: WS-D
created: 2026-08-04T13:27:38Z
updated: 2026-08-05T13:01:16Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-009]
conflicts_with: []
parallel: true
priority: high
estimate: M
story_id: US-005
acceptance_criteria_ids: [AC-005, AC-008, AC-009, AC-010, AC-011, AC-027, AC-035]
---

# Task: Add pinned voice card and provider disclosure

## Description

Add the discoverable full-width `Tell Ownkey what to change` action above the existing preset grid and implement the first-use two-provider disclosure plus specific preflight recovery surfaces without altering preset order or behavior.

## Acceptance Criteria

- [x] The pinned card remains visible above a scrollable two-column preset grid and shows selection/whole-field intent plus configured audio/rewrite provider display names without endpoint URLs.
- [x] Tapping the card invokes the same preflight/session entry as long-press and cannot initialize the microphone merely by opening the Rewrite hub.
- [x] First-use disclosure accurately states direct configured-provider routing, names both providers, and offers Continue, Open AI settings, and cancel/back.
- [x] Missing microphone permission, dictation configuration, rewrite configuration, busy recorder, unsupported target, secure field, and incognito session each show specific inline recovery rather than generic failure.
- [x] In an incognito session the dictation key, voice card, and preset cards render a visibly disabled treatment with `AI is off in incognito mode` and a route to leave incognito or open its setting; no control is silently inert and none is hidden outright.
- [x] Acknowledgement is local and versioned; changing provider values remains visible without forcing disclosure on every use.
- [x] Saved preset cards retain their names, ordering, invocation, selected treatment, and result behavior.
- [x] UI/state tests cover valid selection, no-selection copy, provider names, disclosure versions, every preflight recovery, scrolling, and preset regression.

## Traceability

- Story: US-002, US-005, and US-015.
- Acceptance criteria: AC-005, AC-008, AC-009, AC-010, AC-011, AC-027, AC-035.

## Technical Notes

Use resource-backed user-facing copy and existing Settings -> AI routes. Do not claim Ownkey hosts, relays, or locally processes configured cloud-provider requests.

## Definition of Done

- [x] Pinned card integrated.
- [x] Disclosure/recovery UI integrated.
- [x] Preset behavior preserved.
- [x] UI/state tests pass.

## Evidence Log

- 2026-08-05T13:01:16Z: Rebuilt the rewrite hub around a pinned Tell Ownkey what to change card above a scrollable two-column preset grid: the card states selection or whole-field intent, names both configured providers by preset label rather than endpoint URL, and starts the same preflight as the dictation-key hold. Added the versioned first-use disclosure sheet with Continue, Open AI settings and Back, plus an inline recovery sheet that names each preflight prerequisite; incognito routes to the incognito setting rather than the provider forms. Under incognito or a secure field the dictation key, voice card, and preset cards render visibly disabled with the reason in both copy and accessibility semantics, and the hub reads neither editor content nor provider secrets. Opening the hub performs no network or microphone work. Evidence: 244 app debug unit tests passed (0 failures), including 19 new hub-card, availability, disclosure-version, preflight-recovery and preset-ordering regression cases; :app:compileDebugKotlin passed.

- 2026-08-05T12:50:14Z: Adding the pinned voice card, provider disclosure and preflight recovery

- 2026-08-05T12:50:13Z: T-009 done; hub entry work starts

- 2026-08-04: Task created during delivery decomposition.
