---
name: Predictive Typing Quality & Trust
slug: predictive-typing-quality-trust
owner: ownkey-keyboard-team
status: planned
created: 2026-03-28T11:19:17Z
updated: 2026-03-28T13:11:21Z
outcome: Define a trust-first predictive typing roadmap that can raise top-3 suggestion acceptance by 15%, hold suggestion latency under 50 ms p95, and reduce false autocorrect pain by 30% across EN, NL, and mixed-language flows.
uncertainty: medium
probe_required: false
probe_status: skipped
---

# Spec: Predictive Typing Quality & Trust

## Executive Summary
OwnKey needs a predictive typing roadmap that optimizes for both speed and trust. The strongest public evidence from modern keyboards suggests that good products do not rely on one magic next-word model. They use a hybrid prediction stack: decoder logic for literal input and correction, compact language-model components for speed, richer neural components for context, and personalization layers that respect privacy boundaries. This project defines the planning baseline, evaluation model, and rollout shape needed before a larger implementation push.

### Research anchors
- Google's earlier Gboard decoder work describes a weighted finite-state decoding stack for mobile keyboard correction and prediction, which supports keeping decoder or candidate-generation responsibilities separate from the ranking model instead of assuming one end-to-end model should do everything. Source: [Gboard decoder / FST search](https://arxiv.org/abs/1704.03987).
- Google's newer Gboard search-space work reinforces the same layered idea for neural systems: candidate search space quality matters independently from downstream ranking quality. Source: [Gboard neural search space](https://arxiv.org/abs/2410.15575).
- Keyboard personalization and privacy are not optional add-ons. Public Gboard, SwiftKey, and Apple work all point toward on-device or privacy-preserving adaptation as the practical direction for production keyboards. Sources: [Gboard federated keyboard prediction](https://research.google/pubs/federated-learning-for-mobile-keyboard-prediction-2/), [Gboard federated learning + differential privacy](https://arxiv.org/abs/2305.18465), [SwiftKey differentially private transformer](https://arxiv.org/abs/2505.05648), and [Apple private and personalized frequency estimation](https://machinelearning.apple.com/research/private-and-personalized).

## Problem and Users
OwnKey's most valuable typing moments are also the easiest to break. Fast typers lose rhythm when predictions lag. Multilingual users stop trusting the bar when language routing collapses inside one sentence. Autocorrect becomes harmful when it is technically accurate too often in the wrong moments, such as names, jargon, or code-switching. Mobile keyboards also do more than next-word prediction. They combine literal typing, correction, completion, and sometimes swipe-style decoding under strict on-device latency constraints.

Primary users:
- Fast typers who care more about uninterrupted flow than aggressive automation
- EN/NL multilingual users who switch language mid-sentence
- Users who expect chat, notes, and email contexts to behave differently
- Users who want personalization benefits without losing control or privacy

## Scope and Non-Goals
### In Scope
- Prediction quality and trust planning for top-3 suggestions and autocorrect
- Candidate-generation and decoder-layer requirements for typed input, correction, and completion behavior
- Latency budgets for keystroke-to-suggestion updates and keyboard warm start
- Mixed-language EN/NL ranking, fallback, and correction behavior
- Personalization rules for user vocabulary promotion and suppression
- Recovery patterns such as undo, backspace restore, never-correct, and tuning controls
- KPI definitions, benchmark corpus requirements, and rollout acceptance thresholds
- Mapping the planning scope into Delano contracts and Linear execution objects

### Non-Goals
- Training or shipping a new cloud-hosted language model in this planning pass
- Voice dictation or speech-to-text roadmap work
- Broad visual redesign unrelated to predictive typing trust or clarity
- Direct ingestion of the inaccessible shared ChatGPT research page without a user-provided source list

## Functional Requirements
1. Define a benchmark corpus strategy that covers EN, NL, and mixed-language typing scenarios, including names, slang, code-switching, and domain terms.
2. Define a reference prediction stack that separates decoder/candidate generation, ranking language model behavior, and personalization layers instead of assuming one model can own the full problem.
3. Compare likely method families for each layer, including n-gram components, finite-state or decoder-based search, RNN/GRU-class models, and lightweight transformer-style options where relevant.
4. Define a latency envelope that targets visible suggestion updates under 50 ms p95 and keyboard first-input readiness under 200 ms.
5. Define a trust-first autocorrect policy with confidence gating, reversible actions, and clear suppression rules.
6. Define multilingual routing rules that preserve prediction quality without requiring manual language switching.
7. Define personalization rules that promote useful terms quickly while avoiding noisy or temporary overfitting.
8. Define privacy-preserving adaptation options, including on-device learning boundaries and compatibility with future federated-learning or differential-privacy approaches.
9. Define user-facing controls for tuning aggressiveness, never-correct behavior, and recovery flows.
10. Define measurable success criteria that can be used as rollout gates for internal, beta, and broader release stages.
11. Define an execution-ready backlog and milestone map that can be synchronized to Linear.

## Non-Functional Requirements
- All planning artifacts must use UTC timestamps and avoid absolute local path leakage.
- Metrics and evidence definitions must remain privacy-safe and avoid raw typed-content logging.
- Planning outputs must be specific enough to drive implementation without requiring a second PRD rewrite.
- Quality gates must distinguish latency regressions from trust regressions instead of collapsing both into one KPI.
- The architecture plan must respect on-device memory and latency limits before favoring richer model families.
- Locale strategy must allow different model mixes per language or market if one global route is not defensible.

## Success Metrics
- Top-3 suggestion acceptance: +15% versus current baseline in target scenarios
- Suggestion latency: < 50 ms p95 in representative benchmark runs
- Keyboard warm start plus first useful suggestion: < 200 ms perceived response target
- False autocorrect pain rate: -30% versus baseline
- Mixed-language relevance score: improved versus baseline on EN/NL code-switch corpus
- Personalization time-to-benefit: repeated user terms surface within 1-3 accepted exposures without noisy-term inflation
- Architecture decision clarity: every major predictive-typing layer has an explicit chosen method or ruled-out option with rationale

## Risks and Assumptions
- The shared research page is currently unreadable in this run, so source-specific citations remain a follow-up item even though the local Obsidian note now provides the primary research basis.
- Existing `typing-speed-core` artifacts already cover overlapping implementation territory, so scope boundaries must stay explicit.
- Benchmark quality will be weak if the corpus underrepresents multilingual switching, names, app-context variance, or correction-heavy scenarios.
- A single-model mindset can oversimplify the real keyboard problem and produce the wrong roadmap.
- Aggressive personalization can improve acceptance while silently damaging trust if reversibility is weak.
- Latency wins that degrade ranking quality are not acceptable and must be measured separately.

## Dependencies
- Delano handbook contracts in `HANDBOOK.md`
- Existing product context in `.project/context/`
- Current typing-related implementation history in `.project/projects/typing-speed-core/`
- Research basis in Bart's Obsidian note `Keyboard prediction methodes.md`
- Linear workspace access through the local `linear-mcp-cli` and direct GraphQL fallback
- User follow-up for any source-level citations that need to be reconstructed beyond the local research note
