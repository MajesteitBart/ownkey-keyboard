# Decisions

## 2026-09-17 — Plan Windows parity as three features

Status: implemented in PR #14 (storage, settings, backup, dictation integration, keyboard fix flow); physical-device qualification of local hints remains open in T-005. Recognition vocabulary, exact corrections, and filler removal have different responsibilities. Keep all three visible and testable. No new cleanup model, semantic rewrite, or automatic word collection.

## 2026-09-17 — Separate speech data from typing dictionaries

Use a dedicated speech repository with transactional edits and versioned backup support; the persistence design chosen at implementation time is the file-backed versioned JSON document described below, not Room. Existing typing records have different fields/semantics, and copying them into cloud hints would create an unexpected privacy boundary. Do not use nested JSON in JetPref after the saved-voice persistence failure.

## 2026-09-17 — Apply cleanup only to ordinary dictation

Take an immutable snapshot at recording start. Vocabulary can help ordinary dictation and spoken rewrite instructions; cleanup applies only before ordinary editor insertion. Preserve session validation and represent cleaned-to-empty separately from recognition failure.

## 2026-09-17 — Preserve Windows rule behavior

Retain conservative inventories, selected-language protection, custom overrides, paragraph/case preservation, filler-before-correction order, and ordered correction cascades. Keep explicit empty language lists. A non-cascading matcher would change behavior and is deferred.

## 2026-09-17 — Probe local hints before enabling them

The pinned runtime and model already provide the required API/BPE file. Modified beam search remains a mobile quality/performance question. Compare one beam profile against greedy-without-hints; keep one loaded model, pass words per stream, bound transport, and do not silently fall back to cloud.

## 2026-09-17 — Keep cloud hint support explicit

Use known Mistral `context_bias` support and an explicit compatible `prompt` mode where qualified. Unknown/custom endpoints default to no hint field. Explain vocabulary disclosure; never assume compatibility or impose an invented universal provider word cap.

## 2026-09-17 — File-backed speech repository instead of Room

Implemented as one versioned JSON document written through a temporary file and an atomic move, not as Room entities. The app module has no Robolectric, so Room persistence could not be exercised on the JVM, while the plan's test strategy makes real-file lifecycle coverage essential. The document is a few hundred entries at most, exports as-is into backups, and needs no schema migrations. This is not the nested JetPref JSON the saved-voice incident warned about: it is a dedicated file with typed serialization, unknown keys tolerated on load, unreadable files kept next to the new one, and restore validated before writing.

## 2026-09-17 — Punctuation-only cleanup counts as nothing to insert

The reference rules keep sentence punctuation, so `Uh, um.` cleans to `.` on Windows as well. Android inserts nothing in that case and shows the neutral "only filler words" message; the raw cleaner output stays identical to the reference for parity tests.

## 2026-09-17 — Fix a misheard word from the keyboard

After each ordinary dictation the smartbar offers "Fix a word" for 15 seconds. The chooser lets the user tap the word or phrase, the word is selected in the host editor, and the ordinary keyboard (or another dictation) retypes it; Save stores the correction and, unless switched off, the replacement as a vocabulary word. No in-keyboard text field is needed, the host app's undo keeps working, and the editor session, field and text are verified before anything is selected. When verification fails the heard text is handed to the settings page.

## 2026-09-18 — Local hotwords stay off until the device probe

Review of PR #14 pointed out that a default of on would switch ordinary Orukeet dictation from the measured greedy decoder to the unmeasured beam profile as soon as one word is saved. The preference now defaults to off; the toggle in Personal dictionary → Recognition hints turns it on for the T-005 measurements.

## 2026-09-18 — A newer on-disk dictionary is kept, not downgraded

Loading a document with a higher version than the app supports would have succeeded (unknown keys are ignored) and the next edit would have rewritten it as version 1, dropping newer fields. The file is now moved aside as `.newer-v<version>-<time>`, the entries start empty, and the page says why. Restore already rejected newer backups.

## 2026-09-17 — Cloud hints: automatic only for documented hosts

Automatic mode sends `context_bias` parts to api.mistral.ai and a `prompt` part to api.openai.com, both documented; every other host gets no words unless the user picks the explicit prompt mode. Multipart text parts are now written as UTF-8, which the previous `writeBytes` call did not do.

## 2026-09-17 — Separate research from Orukeet delivery

Orukeet is committed, pushed, and represented by draft PR #13. Personal-dictionary documents live on a separate planning branch and remain planned. This research does not activate implementation or claim device qualification.
