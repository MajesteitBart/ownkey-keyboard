---
id: T-005
name: Physical device qualification of local hints
status: blocked
blocked_owner: ownkey-keyboard-team
blocked_check_back: 2026-09-24T09:00:00Z
workstream: WS-A
created: 2026-09-17T21:40:00Z
updated: 2026-09-17T21:40:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-003]
conflicts_with: []
parallel: false
priority: medium
estimate: M
story_id: US-001
acceptance_criteria_ids: [AC-001]
---

# Task: Physical device qualification of local hints

## Description

Measure Orukeet with the beam-search hotword profile against greedy search on a representative arm64 phone: name accuracy, word error rate on ordinary and mixed-language speech, false insertions with irrelevant names, warm and cold decode time, engine recreation cost when the profile switches, cancellation, and peak PSS. Decide whether the on-device hint toggle stays on by default for internal builds.

## Acceptance Criteria
- [ ] Paired English and Dutch recordings compared with no hints, relevant names and irrelevant names.
- [ ] Aggregate accuracy, latency and memory results published without private audio or transcripts.
- [ ] Default for `ai__local_vocabulary_hints` confirmed or changed based on the results.

## Traceability
- Story: US-001
- Acceptance criteria: AC-001

## Technical Notes

Blocked in the implementation session: no phone was connected and the emulator is x86_64 without a downloaded model. The on-device hint preference defaults to off; the toggle in Personal dictionary → Recognition hints turns the beam profile on for the measurements without touching saved words.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-09-17: blocked, no arm64 device available.
