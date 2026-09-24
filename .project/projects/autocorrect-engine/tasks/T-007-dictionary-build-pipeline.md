---
id: T-007
name: Dictionary build pipeline and contraction rebuild
status: blocked
blocked_owner: ownkey-keyboard-team
blocked_check_back: After dependencies are done: T-004, T-005
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-24T11:31:43Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-004, T-005]
conflicts_with: []
parallel: false
priority: medium
estimate: M
story_id: US-005
acceptance_criteria_ids: []
---

# Task: Dictionary build pipeline and contraction rebuild

## Description

Create `tools/dictionary-build/`, which turns source lists into the shipped EN and NL assets. Frequencies come from FrequencyWords. Validity comes from a whitelist (SCOWL for EN, OpenTaal for NL, after a license check). The pipeline reuses the T-005 removal lists, drops split fragments and rebuilds contractions and elisions.

## Acceptance Criteria

- [ ] License check for each source recorded in `decisions.md` before any new data ships. Attribution files updated.
- [ ] Split and backtick contractions in `en_50k.txt` (`'t`, `'s`, `` don`t ``, `don`, `didn`, `isn`) are rebuilt into real forms ("don't", "it's", "isn't"). Counts of apostrophe-less forms fold into the apostrophe form where the whitelist says the bare form is not a word.
- [ ] Dutch elisions (`z'n`, `m'n`, `d'r`) are present.
- [ ] Frequencies are stored as log-scale values.
- [ ] Glide typing regression check: top-1 accuracy on a fixed set of gestures, or on the glide classifier's word ranking, does not drop against the current release.
- [ ] Other languages still load `data.json` and produce suggestions, checked with at least one non-EN/NL subtype.
- [ ] Heap and load time for EN+NL are measured in MB and ms on the lowest-spec test device and stay within the spec budget. If they don't, start the conditional trie phase in `plan.md`.
- [ ] The T-004 thresholds are recalibrated on the new assets and every Phase 1 gate still holds.

## Traceability

- Story: US-005
- Acceptance criteria: none directly, T-008 delivers AC-006

## Technical Notes

- License checks and tooling can start once T-001 is done. Only the asset swap waits for T-004 and T-005, so thresholds are recalibrated once.
- Keep the old `*_50k.txt` assets until the new format has shipped in a stable release.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created from dictionary inspection. `en_50k.txt` has 0 of 14 common contractions as words and 14 of 14 apostrophe-less forms.
