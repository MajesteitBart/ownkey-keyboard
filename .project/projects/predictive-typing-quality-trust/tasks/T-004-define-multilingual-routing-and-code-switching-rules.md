---
id: T-004
name: Define multilingual routing and code-switching rules
status: blocked
created: 2026-03-28T11:19:17Z
updated: 2026-03-28T11:35:00Z
linear_issue_id: 5768e53f-4136-4e2f-9c50-a1b77db63c1e
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: []
parallel: true
priority: high
estimate: M
workstream: WS-3
blocked_owner: ownkey-keyboard-team
blocked_check_back: After dependencies are done: T-001
---

# Task: Define multilingual routing and code-switching rules

## Description
Specify how EN, NL, and mixed-language input should be ranked, corrected, and recovered without manual language switching, while leaving room for locale-specific model choices.

## Acceptance Criteria
- [ ] The plan covers sentence-level code-switching, name-heavy input, and app-context variance.
- [ ] Fallback behavior is defined for uncertain language classification.
- [ ] The routing plan states whether different locales or markets may need different model mixes or thresholds.

## Technical Notes
Prioritize stable user trust over aggressive language guessing, and do not assume a single global route fits every locale equally well.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-03-28: Synced to Linear as `BAR-71` (https://linear.app/bartvandermeeren/issue/BAR-71/t-004-define-multilingual-routing-and-code-switching-rules).
- 2026-03-28: Task created during predictive typing planning pass.
