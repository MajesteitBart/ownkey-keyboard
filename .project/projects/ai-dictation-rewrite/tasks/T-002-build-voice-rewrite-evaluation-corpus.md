---
id: T-002
name: Build voice rewrite evaluation corpus
status: ready
created: 2026-05-01T11:07:28Z
updated: 2026-05-01T11:07:28Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: []
parallel: true
priority: high
estimate: M
---

# Task: Build voice rewrite evaluation corpus

## Description
Create a small EN/NL corpus of voice-like dictated snippets with repetitions, stutters, false starts, missing punctuation, and rambling structure.

## Acceptance Criteria
- [ ] Corpus includes English, Dutch, and mixed-language cases.
- [ ] Each case records expected properties: preserve meaning, remove repetition, preserve language, avoid over-formalization.
- [ ] Corpus includes short chat, email, and note-like examples.
- [ ] Exact-output matching is avoided unless the case is deterministic.

## Technical Notes
This corpus should evaluate rewrite behavior, not speech recognition accuracy.

## Definition of Done
- [ ] Corpus artifact added under the project or test fixtures
- [ ] Review checklist added
- [ ] Used by provider probe task

## Evidence Log
- 2026-05-01: Task created.
