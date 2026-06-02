---
id: WS-3
name: WS-3 Multilingual Personalization Strategy
owner: language-modeling-team
status: planned
created: 2026-03-28T11:19:17Z
updated: 2026-03-28T11:27:04Z
linear_milestone_id: 2897763b-449d-45bc-adae-4c292f2fe0c9
linear_label_id: 78a03d13-7f99-4909-bb36-604b944a4e21
---

# Workstream: WS-3 Multilingual Personalization Strategy

## Objective
Define how OwnKey handles EN/NL code-switching and user-specific vocabulary without poisoning predictions or crossing privacy boundaries.

## Owned Files/Areas
- Mixed-language routing and ranking behavior
- Personal dictionary partitioning and promotion rules
- Never-correct and suppression policy inputs

## Dependencies
- WS-1 corpus coverage for multilingual and name-heavy scenarios
- WS-2 trust policy for suppression and reversibility

## Conflict Risk Zones
- Shared dictionary-policy definitions that can affect both autocorrect trust and rollout metrics

## Handoff Criteria
- Mixed-language decision rules are documented and testable
- Personalization policy balances speed-to-benefit with noise control
