---
id: T-001
name: Define benchmark corpus and measurement protocol
status: ready
created: 2026-03-28T11:19:17Z
updated: 2026-03-28T13:11:21Z
linear_issue_id: e6a72d3d-5fa4-43d6-8f02-e92b70d93efe
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: true
priority: high
estimate: M
---

# Task: Define benchmark corpus and measurement protocol

## Description
Specify the scenario set, input categories, and scoring rules used to evaluate predictive typing quality across EN, NL, and mixed-language typing, including completion, autocorrect, and decoder-heavy correction cases.

## Acceptance Criteria
- [ ] Corpus categories cover monolingual, code-switching, names, slang, domain terms, and correction-heavy cases.
- [ ] Scoring rules distinguish latency, ranking quality, trust failures, and decoder-level recovery quality.

## Technical Notes
This task creates the evaluation backbone for every later product decision and must compare the real keyboard stack, not only next-word prediction in isolation. Benchmark scenarios should explicitly cover decoder-heavy correction and candidate-generation behavior because public Gboard work frames those layers as first-class system components, not background plumbing. Sources: [1704.03987](https://arxiv.org/abs/1704.03987), [2410.15575](https://arxiv.org/abs/2410.15575).

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-03-28: Synced to Linear as `BAR-68` (https://linear.app/bartvandermeeren/issue/BAR-68/t-001-define-benchmark-corpus-and-measurement-protocol).
- 2026-03-28: Task created during predictive typing planning pass.
