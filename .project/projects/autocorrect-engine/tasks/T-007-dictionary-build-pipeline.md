---
id: T-007
name: Dictionary build pipeline and contraction rebuild
status: done
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-25T14:23:05Z
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

- [x] License check for each source recorded in `decisions.md` before any new data ships. Attribution files updated.
- [x] Split and backtick contractions in `en_50k.txt` (`'t`, `'s`, `` don`t ``, `don`, `didn`, `isn`) are rebuilt into real forms ("don't", "it's", "isn't"). Counts of apostrophe-less forms fold into the apostrophe form where the whitelist says the bare form is not a word.
- [x] Dutch elisions (`z'n`, `m'n`, `d'r`) are present.
- [x] Frequencies are stored as log-scale values.
- [x] Glide typing regression check: top-1 accuracy on a fixed set of gestures, or on the glide classifier's word ranking, does not drop against the current release.
- [x] Other languages still load `data.json` and produce suggestions, checked with at least one non-EN/NL subtype.
- [x] Heap and load time for EN+NL are measured in MB and ms and stay within the spec budget. Only the API 35 emulator was available as a device, so the device numbers come from a debug build there; the heap comparison comes from the JVM, because on-device heap deltas were dominated by GC timing. No trigger for the conditional trie phase.
- [x] The T-004 thresholds are recalibrated on the new assets and every Phase 1 gate still holds.

## Traceability

- Story: US-005
- Acceptance criteria: none directly, T-008 delivers AC-006

## Technical Notes

- License checks and tooling can start once T-001 is done. Only the asset swap waits for T-004 and T-005, so thresholds are recalibrated once.
- Keep the old `*_50k.txt` assets until the new format has shipped in a stable release.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-25: The 40,821 EN words in the entry below were counted before T-008 folded `heres` and `wheres` and removed `mn`; the shipped list has 40,818 EN words. `build.py` now pins OpenTaal to commit 08879a9c (the last change to its word list, 2023-03-10) and checks both sources by SHA-256; the pinned build reproduces both shipped lists byte for byte.
- 2026-09-24: `tools/dictionary-build/build.py` builds `app/src/main/assets/ime/dict/latin/{en,nl}.txt` (format `word<TAB>round(100 * ln(count))`, parsed by `LatinText.parseDictionary`). Validity: SCOWL 2020.12.07 sizes up to 70 plus names up to 95 (English), OpenTaal (Dutch). A word outside the validity list stays when it ranks in the top 3,000, or in the top 20,000 without a 10× more frequent word one edit away. Removal lists from T-005 still apply. English contractions are rebuilt from the split tokens (don't 4.2M, I'm 4.4M, can't 1.3M, won't 0.4M, it's 2.6M, you're 2.9M); dont, im, youre, didnt and the other non-word forms fold into them; cant, ill, its, well stay as words. Dutch gets z'n, m'n, d'r and zo'n from the orphan letters and a Tatoeba ratio; the orphan letters go, u stays. Result: 40,821 EN and 41,827 NL words. Licenses checked: SCOWL notice license and OpenTaal BSD-3-Clause, both compatible with Apache-2.0; attribution in `latin/ATTRIBUTION.md` and AboutLibraries entries (FrequencyWords, SCOWL with its notice, OpenTaal).
- 2026-09-24: A first build kept only listed words and cost 21 false corrections per 7,999 clean English words (Sami → Same, Oleg → Leg), because names at sentence start lost their dictionary entry. Fixed with the name lists and the unlisted-word rule; clean English is back to 0.
- 2026-09-24: `BuiltDictionaryRegressionTest`: glide frequency order preserved (Spearman 0.998 EN, 0.99995 NL; 4,924 and 4,956 of the top 5,000 kept); `data.json` still builds; log100 round-trips within 1%; built models smaller than raw (JVM: 16 MB EN, 18 MB NL for both; build 52 vs 69 ms EN, 57 vs 68 ms NL). Benchmark after the swap: Phase 1 floors and strength calibration still pass; EN missing apostrophes 12 of 14 autocorrected and 14 of 14 first (was 0 of 14); real EN 82%, real NL 90%, all with 0 wrong; clean text at most 0.13 per 1,000. The apostrophe omission cost (1.5) was added to the scorer so `dont` reaches "don't".
- 2026-09-24: Emulator (API 35, debug build): warm load EN built 1,073 ms vs raw 989 ms, NL built 826 ms vs raw 867 ms; the first cold load after install took 4.3 s (debug build, runs off the main thread). Typing `dont thats im wrld` gave "Don't that's im world" (`im` is T-008). A German (Austria) QWERTZ subtype still shows suggestions from `data.json`.

- 2026-09-24: Task created from dictionary inspection. `en_50k.txt` has 0 of 14 common contractions as words and 14 of 14 apostrophe-less forms.
