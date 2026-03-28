---
name: Predictive Typing Quality & Trust
status: planned
lead: ownkey-keyboard-team
created: 2026-03-28T11:19:17Z
updated: 2026-03-28T13:11:21Z
linear_project_id: 8e478486-d8e6-44e4-bac7-5a55c6bbdba8
---

# Delivery Plan: Predictive Typing Quality & Trust

## Architecture Decisions
- Keep this project planning-focused and separate from `typing-speed-core`, which already contains implementation-era task history.
- Plan around a hybrid keyboard stack: decoder and candidate generation, language-model ranking, multilingual routing, and personalization are separate concerns that must be tuned together. This matches public Gboard evidence across both FST decoder work and newer neural search-space work rather than a single-model story. Sources: [1704.03987](https://arxiv.org/abs/1704.03987), [2410.15575](https://arxiv.org/abs/2410.15575).
- Treat latency, ranking quality, and trust errors as separate decision axes, not one blended score.
- Use trust-first defaults: prediction help should degrade gracefully instead of correcting aggressively under uncertainty.
- Assume compact methods such as finite-state search or n-gram components may still be the right fit for some low-latency layers, even if richer neural models are used elsewhere.
- Treat GRU/RNN-class and lightweight transformer options as candidate ranking layers, not automatic replacements for the whole stack.
- Design multilingual and personalization policies together so language-mixing does not poison learned vocabulary.
- Keep future federated-learning and differential-privacy options compatible with the planning baseline, even if no training approach is selected yet. Sources: [Gboard FL keyboard prediction](https://research.google/pubs/federated-learning-for-mobile-keyboard-prediction-2/), [Gboard FL + DP](https://arxiv.org/abs/2305.18465), [SwiftKey DP transformer](https://arxiv.org/abs/2505.05648), [Apple private personalization](https://machinelearning.apple.com/research/private-and-personalized).
- Make reversibility a first-class product constraint through undo, suppression, and explainable tuning surfaces.

## Workstream Design
- **WS-1 Benchmark and Latency Budget**: define corpus coverage, measurement rules, architecture-comparison criteria, and target envelopes.
- **WS-2 Trust-first Correction UX**: define when OwnKey should predict, autocorrect, back off, or help users recover.
- **WS-3 Multilingual Personalization Strategy**: define EN/NL mixing, dictionary boundaries, privacy guardrails, and learning behavior.
- **WS-4 Rollout Readiness and KPI Governance**: convert planning into milestones, issue sequencing, and go/no-go gates.

## Milestone Strategy
1. **M1 Benchmark Baseline**: lock corpus coverage, KPI definitions, architecture comparison rules, and latency budget.
2. **M2 Trust Policy Definition**: finalize autocorrect, undo, suppression, and tuning rules.
3. **M3 Multilingual Personalization Plan**: finalize mixed-language behavior, user-vocabulary learning, and privacy boundaries.
4. **M4 Delivery Readiness**: convert the planning package into an implementation-ready roadmap with acceptance thresholds.

## Rollout Strategy
- Use this project as the planning and alignment layer before adding or reshaping implementation tickets.
- Validate proposed changes first against benchmark and trust criteria, then stage into internal, beta, and broader rollout decisions.
- Hold rollout on any milestone that improves speed while materially degrading user trust signals.
- Treat locale strategy as configurable: if EN, NL, and mixed-language flows need different model mixes or thresholds, preserve that option instead of forcing one compromise setting.

## Test Strategy
- Review all planning outputs against handbook contract requirements and path-privacy constraints.
- Validate benchmark, KPI, and rollout rules for traceability back to one or more user risks.
- Compare candidate stack choices against the research-backed method families: decoder or finite-state search, compact LM layers, richer neural ranking, and personalization strategy.
- Smoke-check Linear sync by ensuring project, milestones, and initial issues map cleanly back into local frontmatter and registry state.
- Treat repository-wide path leakage in older project artifacts as a separate pre-existing validation issue, not a failure of this new scope.

## Rollback Strategy
- If Linear sync becomes inconsistent, keep local Delano artifacts authoritative and document the manual cleanup required.
- If milestone boundaries prove too overlapping, collapse them locally before any execution work starts.
- If planning scope drifts into implementation details that duplicate `typing-speed-core`, stop and split the overlap into explicit follow-up notes.
- If a proposed model direction cannot defend its latency, trust, or privacy trade-offs, fall back to the simpler viable layer instead of forcing architectural novelty.

## Linear Synchronization
- Linear project: `8e478486-d8e6-44e4-bac7-5a55c6bbdba8`
- Project URL: https://linear.app/bartvandermeeren/project/predictive-typing-quality-and-trust-774d3d5b8a58
- Team: `BAR` (`Bartvandermeeren`)
- Milestones created:
  - `M1 Benchmark Baseline`: `90330589-1cb2-476e-84e1-2892baa0140e`
  - `M2 Trust Policy Definition`: `abc7199a-9a91-4125-b49e-ed15f90646c8`
  - `M3 Multilingual Personalization Plan`: `2897763b-449d-45bc-adae-4c292f2fe0c9`
  - `M4 Delivery Readiness`: `402d61ef-4959-4668-aaa1-b618dd8cdd1f`
- Workstream label fallback was added to preserve workstream grouping because the generated Linear CLI advertised `create-milestone` and `create-issue` wrappers that were not actually backed by available MCP tools in this environment. Project and issue creation were completed via direct Linear GraphQL mutations using the existing API key.
