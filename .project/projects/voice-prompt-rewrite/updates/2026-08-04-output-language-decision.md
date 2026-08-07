---
timestamp: 2026-08-04T17:01:03Z
status: complete
task: T-010
stream: scoped-change
---

# Progress Update

## Completed

- Accepted D-008: a voice rewrite returns text in the captured source text's language, an explicit language request in the instruction overrides that, and the language the instruction is spoken in never decides the output on its own. Mixed-language source text is preserved.
- Rewrote US-004 from an untestable aspiration into the actual rule, and added AC-037 and AC-038 to cover source-language retention and explicit translation.
- Added FR-057 through FR-059: the rule lives in the fixed rewrite policy, no local language detection or subtype/locale-derived language decision is permitted, and the configured dictation language hint passes through unchanged.
- Added AD-010 to the plan explaining why the rule sits in the fixed policy rather than in app code, and what that costs in testability.
- Split verification honestly: T-010 and T-017 assert the deterministic half (policy carries the rule, nothing local decides language); T-018 verifies real output-language behavior against each configured provider.
- Recorded the dictation `Language hint` interaction: it defaults to empty, meaning auto-detect, so cross-language instructions work by default. A user-set hint narrows recognition, so voice rewrite documents that limitation rather than silently overriding a setting the user chose.

## In Progress

- Spec and plan remain `planned` with `probe_status: pending`. This decision does not activate delivery.

## Blockers

- Unchanged: T-002 is still blocked on the persistent-undo decision (NC-002), which continues to block T-003 and all production work.

## Next Actions

- Resolve NC-002 to unblock T-002.
- Execute T-001, still the only dependency-safe task.

## Notes

- US-004 was the last story in the project with no acceptance criterion and no owning task. All stories and all 38 acceptance criteria now resolve to at least one task.
- Output-language quality is provider-dependent by design. If a configured provider proves unreliable at holding the source language, that surfaces in T-018 as a provider limitation rather than a code defect, and the response is documentation or provider guidance rather than adding local detection.
