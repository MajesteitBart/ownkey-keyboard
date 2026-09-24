---
id: T-009
name: Bigram corpus probe
status: done
workstream: WS-A
created: 2026-09-24T20:57:41Z
updated: 2026-09-24T20:57:41Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: false
priority: high
estimate: S
story_id: US-006
acceptance_criteria_ids: []
---

# Task: Bigram corpus probe

## Description

Pick a license-compatible corpus for per-language bigram counts and measure whether it fits the size budget of at most 3 MB compressed per language (open question 1 in `decisions.md`).

## Acceptance Criteria

- [x] The license of each candidate source is checked against shipping inside an Apache-2.0 app, with the source named.
- [x] Sizes are measured at several pruning levels for EN and NL.
- [x] A held-out split exists so the benchmark never tests on training sentences.
- [x] The decision is recorded in `decisions.md`.

## Traceability

- Story: US-006
- Acceptance criteria: none (Phase 3 gate in plan.md)

## Technical Notes

- Candidates from the plan: OpenSubtitles through OPUS, Tatoeba, Wikipedia. The Leipzig Corpora Collection was added as a fourth.
- `tools/dictionary-build/bigrams.py --probe` prints the sizes without writing assets.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: Tatoeba per-language exports are CC BY 2.0 FR, part also CC0 (tatoeba.org downloads page). Attribution in the app and in `latin/ATTRIBUTION.md` covers it, like FrequencyWords. English has 2,036,986 sentences and Dutch 201,168.
- 2026-09-24: OpenSubtitles through OPUS asks for a link to opensubtitles.org and a citation, but states no license for redistributing the text or data derived from it. Not used. The Leipzig Corpora Collection is described as CC BY by third parties, but its own terms page blocks automated reading and the mirrors state no license. Not used until someone confirms the terms by hand. Wikipedia (CC BY-SA 4.0) stays an option for Dutch coverage if Tatoeba turns out too thin; it is formal text and a large download.
- 2026-09-24: Probe with the shipped EN and NL word lists as vocabulary, sentence parts split at . ! ? ; : and sentences with an id divisible by 10 held out. English: 1,832,178 training sentences, 14.1M tokens, 1.14M distinct bigrams; 313,398 bigrams seen at least 3 times cover 92.5% of pair occurrences in 1.19 MB gzip. Dutch: 180,017 sentences, 1.17M tokens, 238,139 distinct bigrams; 84,040 seen at least twice cover 86.2% in 0.30 MB gzip. Both fit the 3 MB budget with room to spare.
- 2026-09-24: Tatoeba favors a few stock names ("Tom", "Mary"). The build caps their count at 2% of each previous word's successor total, so they do not dominate next-word predictions.
