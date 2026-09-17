# Decisions

## 2026-09-17 — Plan Windows parity as three features

Status: proposed for implementation; grounded in completed research. Recognition vocabulary, exact corrections, and filler removal have different responsibilities. Keep all three visible and testable. No new cleanup model, semantic rewrite, or automatic word collection.

## 2026-09-17 — Separate speech data from typing dictionaries

Use a dedicated Room repository with transactional edits and versioned backup support. Existing typing records have different fields/semantics, and copying them into cloud hints would create an unexpected privacy boundary. Do not use nested JSON in JetPref after the saved-voice persistence failure.

## 2026-09-17 — Apply cleanup only to ordinary dictation

Take an immutable snapshot at recording start. Vocabulary can help ordinary dictation and spoken rewrite instructions; cleanup applies only before ordinary editor insertion. Preserve session validation and represent cleaned-to-empty separately from recognition failure.

## 2026-09-17 — Preserve Windows rule behavior

Retain conservative inventories, selected-language protection, custom overrides, paragraph/case preservation, filler-before-correction order, and ordered correction cascades. Keep explicit empty language lists. A non-cascading matcher would change behavior and is deferred.

## 2026-09-17 — Probe local hints before enabling them

The pinned runtime and model already provide the required API/BPE file. Modified beam search remains a mobile quality/performance question. Compare one beam profile against greedy-without-hints; keep one loaded model, pass words per stream, bound transport, and do not silently fall back to cloud.

## 2026-09-17 — Keep cloud hint support explicit

Use known Mistral `context_bias` support and an explicit compatible `prompt` mode where qualified. Unknown/custom endpoints default to no hint field. Explain vocabulary disclosure; never assume compatibility or impose an invented universal provider word cap.

## 2026-09-17 — Separate research from Orukeet delivery

Orukeet is committed, pushed, and represented by draft PR #13. Personal-dictionary documents live on a separate planning branch and remain planned. This research does not activate implementation or claim device qualification.
