# Ownkey IME pipeline: typing to suggestions and autocorrect

Status: read-only code analysis of branch `feat/prediction-vs-gboard` at base commit `12fa074c` (2026-11-07).
It is meant as the reference for (a) an offline simulator of on-device behaviour and (b) prediction and autocorrect
work for the benchmark in `tools/kbbench/PROTOCOL.md`. Every claim cites `file:line`. Statements about Gboard are
outside knowledge and are marked as such.

## File legend

All paths are relative to the repository root. `K/` = `app/src/main/kotlin/dev/patrickgold/florisboard/`.

| Short name | Path |
|---|---|
| `TextKeyboardLayout.kt` | `K/ime/text/keyboard/TextKeyboardLayout.kt` |
| `TextKeyboard.kt` | `K/ime/text/keyboard/TextKeyboard.kt` |
| `InputEventDispatcher.kt` | `K/ime/input/InputEventDispatcher.kt` |
| `KeyboardManager.kt` | `K/ime/keyboard/KeyboardManager.kt` |
| `AutocorrectUndoTracker.kt` | `K/ime/keyboard/AutocorrectUndoTracker.kt` |
| `EditorInstance.kt` | `K/ime/editor/EditorInstance.kt` |
| `AbstractEditorInstance.kt` | `K/ime/editor/AbstractEditorInstance.kt` |
| `EditorContent.kt` | `K/ime/editor/EditorContent.kt` |
| `PendingSeparatorSuggestionContent.kt` | `K/ime/editor/PendingSeparatorSuggestionContent.kt` |
| `AcceptedSuggestionSpacingPolicy.kt` | `K/ime/editor/AcceptedSuggestionSpacingPolicy.kt` |
| `NlpManager.kt` | `K/ime/nlp/NlpManager.kt` |
| `NlpProviders.kt` | `K/ime/nlp/NlpProviders.kt` |
| `SuggestionCandidate.kt` | `K/ime/nlp/SuggestionCandidate.kt` |
| `LatinLanguageProvider.kt` | `K/ime/nlp/latin/LatinLanguageProvider.kt` |
| `LatinPredictionShortcuts.kt` | `K/ime/nlp/latin/LatinPredictionShortcuts.kt` |
| `HighCertaintyAutocorrectPolicy.kt` | `K/ime/nlp/latin/HighCertaintyAutocorrectPolicy.kt` |
| `AppSpecificAutocorrectProfilePolicy.kt` | `K/ime/nlp/latin/AppSpecificAutocorrectProfilePolicy.kt` |
| `MixedLanguageScoringPolicy.kt` | `K/ime/nlp/latin/MixedLanguageScoringPolicy.kt` |
| `RapidPersonalVocabularyLearner.kt` | `K/ime/nlp/latin/RapidPersonalVocabularyLearner.kt` |
| `NeverCorrectWords.kt` | `K/ime/nlp/latin/NeverCorrectWords.kt` |
| `PersonalNgramStore.kt` | `K/ime/nlp/personal/PersonalNgramStore.kt` |
| `PersonalDataStore.kt` | `K/ime/nlp/personal/PersonalDataStore.kt` |
| `TypingSpeedMetrics.kt` | `K/ime/nlp/TypingSpeedMetrics.kt` |
| `EmojiSuggestionProvider.kt` | `K/ime/media/emoji/EmojiSuggestionProvider.kt` |
| `DictionaryManager.kt` | `K/ime/dictionary/DictionaryManager.kt` |
| `CandidatesRow.kt` | `K/ime/smartbar/CandidatesRow.kt` |
| `Smartbar.kt` | `K/ime/smartbar/Smartbar.kt` |
| `Subtype.kt` | `K/ime/core/Subtype.kt` |
| `SubtypeManager.kt` | `K/ime/core/SubtypeManager.kt` |
| `AppPrefs.kt` | `K/app/AppPrefs.kt` |
| `Routes.kt` | `K/app/Routes.kt` |
| `SubtypeEditorScreen.kt` | `K/app/settings/localization/SubtypeEditorScreen.kt` |
| `SelectLocaleScreen.kt` | `K/app/settings/localization/SelectLocaleScreen.kt` |
| `LocalizationScreen.kt` | `K/app/settings/localization/LocalizationScreen.kt` |
| `FlorisDropdownLikeButton.kt` | `lib/compose/src/main/kotlin/org/florisboard/lib/compose/FlorisDropdownLikeButton.kt` |
| `l10n/extension.json` | `app/src/main/assets/ime/keyboard/org.florisboard.localization/extension.json` |
| `en_50k.txt`, `nl_50k.txt` | `app/src/main/assets/ime/dict/frequencywords/` |
| `proguard-rules.pro`, `build.gradle.kts` | `app/` |

## 0. Headline findings

1. **Autocorrect never fires with default settings.** For a distance-1 correction the confidence is
   `0.55*f + 0.25*0.75 + 0.15*prefix + 0.05*lengthCloseness` with `f = freq/maxFreq` linear
   (`LatinLanguageProvider.kt:1045-1062`). It is then blended with the language weight `0.88*c + 0.12*w`
   (`MixedLanguageScoringPolicy.kt:51-53`). The gate is `confidence >= 0.88` (`AppPrefs.kt:178-181`,
   `HighCertaintyAutocorrectPolicy.kt:45`). With one language (`w = 1`) a non-prefix correction peaks at
   `0.88*0.7875 + 0.12 = 0.813`, even when the target is the most frequent word in the list. Real
   corrections score about 0.27 to 0.34. An offline re-implementation of `suggest()` on the bundled lists gave
   **0 auto-corrections for 600 synthetic typos** (nl-only and nl+en), although the intended word was the top
   candidate for about 65% of them. Ownkey's residual WER is therefore close to the raw noisy-typing error rate.
2. **Completions rank shortest first and recall is capped.** The rank score is
   `f + 0.35*prefix - distancePenalty - 0.18*|Δlen|/len - shortWordPenalty` (`LatinLanguageProvider.kt:992-1005`).
   Because `f` is a linear fraction of the top word count (about 1e-4 for ordinary words), the length penalty
   decides the order, so the shortest completion wins. Prefix lookup only searches the 48 most frequent words
   that share the first `min(3, len)` letters (`LatinPredictionShortcuts.kt:44-54,69-85`). For example `consi`
   offers only consider, considered and considering.
3. **Next-word prediction is close to a unigram.** It mines repeats in the current field, reads personal n-grams
   (learning is off in the benchmark field) and otherwise falls back to the most frequent words: `ik, je, het`
   for nl and `you, the, to` for en (`LatinLanguageProvider.kt:806-853,1007-1043`; `LatinPredictionShortcuts.kt:55-66`).
   There are **no suggestions at all on an empty field** (`LatinLanguageProvider.kt:779-781`).
4. **The default Smartbar is not "3 slots".** `DYNAMIC_SCROLLABLE` (`AppPrefs.kt:743-746`) shows up to 8
   variable-width candidates left to right, with the top candidate on the **left** (`CandidatesRow.kt:80-134`).
5. **Nobody can set up Dutch + English today.** No Dutch preset exists (`l10n/extension.json:85` onward, 72
   presets, none `nl`). `secondaryLocales` has no editor UI (`SubtypeEditorScreen.kt:211` is commented out).
   The provider does support several locales (`LatinLanguageProvider.kt:519-557`).
6. **The "Primary language" bug is most likely not R8.** The likely cause is a press-state hack in
   `FlorisDropdownLikeButton.kt:49-54`: `onClick()` runs during composition only if `pressed` is seen as true,
   so a zero-duration `adb shell input tap` is lost. See section 5. Confidence: about 75%.
7. **No-personalized-learning fields block n-gram and e-mail learning only.** Prediction still reads earlier
   learned data. Accepting or reverting a suggestion still writes the user dictionary and the never-correct list
   (`KeyboardManager.kt:331-333`, `LatinLanguageProvider.kt:437-484`).

---

## 1. From a key tap to the NLP call

### 1.1 Touch to key code

- Touch events reach `TextKeyboardLayoutController.onTouchEventInternal` (`TextKeyboardLayout.kt:511`). This
  happens after the swipe detector (`:513`) and, if glide is on, the glide detector (`:514-528`). Glide is off by
  default (`AppPrefs.kt:384-387`).
- `ACTION_DOWN` calls `onTouchDownInternal` (`TextKeyboardLayout.kt:532-540`). There the key is resolved
  **hard** with `keyboard.getKeyForPos(event.getX, event.getY)` (`TextKeyboardLayout.kt:648`). That is a linear
  scan for the first key whose `touchBounds` contain the point (`TextKeyboard.kt:40-47`). Only
  `key.computedData` goes on to `inputEventDispatcher.sendDown` (`TextKeyboardLayout.kt:650-651`).
- A move outside `visibleBounds` ±10% horizontally or ±35% vertically cancels the key and re-resolves it
  (`TextKeyboardLayout.kt:724-731`).
- On `ACTION_UP`, `onTouchUpInternal` calls `sendUp`, or `sendCancel` + `sendDownUp` if the key data changed
  (`TextKeyboardLayout.kt:736-783`). The dispatcher runs synchronously on the main thread via `runBlocking`
  (`InputEventDispatcher.kt:136-163`) and calls `KeyboardManager.onInputKeyUp` (`KeyboardManager.kt:780`).
- For `KeyType.CHARACTER` in character mode, `onInputKeyUp` first auto-commits a candidate if the character is
  not alphabetic (section 3), then calls `editorInstance.commitChar(text)` (`KeyboardManager.kt:902-912`). It
  then clears one-shot shift (`:918-920`).

### 1.2 Committing the character and the composing region

- `EditorInstance.commitChar` (`EditorInstance.kt:281-304`) first fires `notifyTextBoundaryIfNeeded`, which
  drives learning on separators (`:272-279`). It then resolves deferred auto-space and phantom space and calls
  `AbstractEditorInstance.commitChar` (`AbstractEditorInstance.kt:343-390`).
- With the default `appender` composer (`Subtype.kt:68`), `rm == 0`, so the call becomes `commitTextInternal`
  (`AbstractEditorInstance.kt:367-368,394-423`). That runs `finishComposingText()`, then `commitText(text,1)`,
  pushes a predicted `EditorContent` to `expectedContentQueue`, and calls
  `setComposingRegion(newContent.composing)`. The composing region is only a marker over the current word.
  It is recomputed each time by `generateContent` (`AbstractEditorInstance.kt:229-264`) through
  `NlpManager.determineLocalComposing`, which calls the provider's ICU `BreakIterator` word boundary for the
  primary locale (`NlpProviders.kt:235-257`).
- The composing word is only determined when the editor is rich, `!flagTextNoSuggestions`
  (`AbstractEditorInstance.kt:293-295`), and phantom space is inactive or shows composing
  (`EditorInstance.kt:177-180`). `localComposing` is filled only if composing is enabled and suggestions are on
  (`AbstractEditorInstance.kt:255`, `EditorInstance.kt:169-171`). Composing is enabled for non-password text
  with `prefs.suggestion.enabled` (`EditorInstance.kt:127-136`).

### 1.3 When `NlpManager.suggest` runs

- The only caller is `KeyboardManager.resetSuggestions` (`KeyboardManager.kt:254-264`). It runs on **every
  emission of `editorInstance.activeContentFlow`** (`KeyboardManager.kt:183-185`) and on subtype change
  (`:174-179`).
- `activeContentFlow` is a `StateFlow` that is only set in `handleSelectionUpdate`, that is, after the app's
  `onUpdateSelection` round trip (`AbstractEditorInstance.kt:165-210`). The predicted content in
  `expectedContentQueue` is used by the `activeContent` getter (`AbstractEditorInstance.kt:90-92`), but it does
  **not** trigger suggestions. As a result, suggestions always lag one IPC round trip plus compute time.
- There is **no debounce and no cancellation**. The collector uses `collectIn`, which is `launch { collect }`
  (`lib/kotlin/src/main/kotlin/org/florisboard/lib/kotlin/Flow.kt:26-28`), on a `Dispatchers.Default` scope
  (`KeyboardManager.kt:103`). `suggest()` starts a new `scope.launch` on `Dispatchers.Default + SupervisorJob`
  for each call (`NlpManager.kt:63,197-236`) and never cancels earlier jobs. Results are ordered by request
  time: a result is only stored if `internalSuggestions.first < reqTime` (`NlpManager.kt:226-233`). Each store
  runs `assembleCandidates()` through the observable delegate (`NlpManager.kt:76-78,310-333`). If two requests
  share the same millisecond, the strict `<` can keep the older result.
- `maxCandidateCount = 8` is hard-coded for words (`NlpManager.kt:220`). Emoji uses
  `prefs.emoji.suggestionCandidateMaxCount`, default 5 (`NlpManager.kt:205`, `AppPrefs.kt:324-327`).
- Before the provider runs, `PendingSeparatorSuggestionContent.forRefresh` adds a virtual `" "` when a phantom
  space is pending after an accepted suggestion. This makes next-word prediction run straight away
  (`PendingSeparatorSuggestionContent.kt:28-58`).
- Latency is recorded per request (`NlpManager.kt:234`) only in debug or beta builds
  (`TypingSpeedMetrics.kt:41-42`). It is exported in Devtools > Export debug log
  (`K/lib/devtools/Devtools.kt:62-65`).

### 1.4 `EditorContent` fields (`EditorContent.kt:31-111`)

| Field | Content |
|---|---|
| `text` | Up to 256 chars before the cursor, plus the selection, plus up to 128 chars after it (`AbstractEditorInstance.kt:56-57,190-193,258-262`) |
| `textBeforeSelection` | `text[0, selection.start)`; **includes the word being typed** (`EditorContent.kt:43-44`) |
| `composingText` | The composing range text; empty when composing is disabled (`EditorContent.kt:78-79`) |
| `currentWordText` | The BreakIterator word before the cursor even when composing is disabled (`EditorContent.kt:91-92`) |

`LatinLanguageProvider.suggest` uses `rawInput = composingText.ifBlank { currentWordText }.trim()`
(`LatinLanguageProvider.kt:256`). Context comes from `textBeforeSelection`.

### 1.5 Raw touch coordinates

They are **not available anywhere near the NLP layer.** The coordinates exist only in `MotionEvent` handling
(`TextKeyboardLayout.kt:648,717-727`) and in glide gesture points (`TextKeyboardLayout.kt:1012`). Only `KeyData`
crosses `InputEventDispatcher`, and providers only receive `EditorContent` (`NlpProviders.kt:137-143`).

Passing them is moderate work, roughly 150 to 300 lines:

1. Record `(char, x, y)` in a per-word touch buffer in `onTouchUpInternal` before `sendUp`
   (`TextKeyboardLayout.kt:736-783`).
2. Clear the buffer on separators, cursor moves, deletes and suggestion commits (`KeyboardManager.kt:498-525,637-666`).
3. Expose it through a side channel, or as an extra field next to `EditorContent`.
4. In `suggest()`, use it only if the buffered characters equal the current `rawInput`, otherwise fall back.

Key geometry is already handed out to the glide classifier (`TextKeyboardLayout.kt:157-160` →
`K/ime/text/gestures/GlideTypingManager.kt:76-79`). A key-centre spatial model without raw coordinates is the
cheaper first step.

---

## 2. Suggestion generation and display

### 2.1 In-word algorithm (`LatinLanguageProvider.suggest`, `LatinLanguageProvider.kt:232-405`)

Port this exactly into the simulator.

1. **Cache.** The LRU holds 128 entries (`:62,153-159`) and is checked first (`:251-253`). The key is the locale
   list, `composingText`, `currentWordText`, flags, the policy signature and whether the field is an e-mail field.
   **`textBeforeSelection` (96-char tail) is only part of the key when there is no current word**
   (`:1222-1249`). Context-dependent language weights (`:276-281`) and personal boosts (`:335-341`) are
   therefore cached for each word regardless of the preceding words. The cache lives as long as the IME
   process, across benchmark sentences.
2. **Normalise.** `normalizeInputWord` trims, maps `’` to `'` and lowercases with the primary locale (`:1113-1117`).
   Dictionary words are lowercased with `Locale.ROOT` (`:1107-1111`).
3. **Languages.** One `SubtypeLanguageContext` is built for each distinct language in `subtype.locales()`, and
   index 0 is primary (`:519-557`). Models are `en_50k.txt` / `nl_50k.txt` (`:72-75,602-625`). Any other
   language falls back to the legacy `ime/dict/data.json` (`:572-600,627-638`).
4. **Language weights.** These are computed from the last 6 tokens of `textBeforeSelection`, which include the
   partial word (`:917-920`). Each token found in a model adds `0.6 + 0.4*(i+1)/n` (`:927-935`). The raw score is
   `prior (1.0 primary / 0.78 secondary) + 0.40*evidence + 0.42 if the input is an exact word`, normalised to sum
   to 1 (`MixedLanguageScoringPolicy.kt:26-44`).
5. **Candidates per language,** capped at 16 (`MaxLookupCandidateCount`, `:60`):
   - the exact input if it is in the model, with distance 0 and prefix=true (`:290-298`);
   - prefix completions from `LatinPredictionShortcuts.lookupPrefixCandidates(input, 16)` with **distance 0**
     (`:300-304,755-769`). The bucket is the top 48 by frequency for prefix `input[0, min(3,len))`, then filtered
     with `startsWith(input) && != input` (`LatinPredictionShortcuts.kt:39-54,69-85`);
   - corrections, only when `len(input) >= 4` (`MinLengthForTypoCorrections`, `:61,1131-1133`). These come from a
     symmetric-delete index at edit distance 1 (`MaxEditDistance = 1`, `:59`). The index covers the top 20,000
     words of length 2 to 18 (`:69-70,640-669`). Lookup uses the deletes of the input plus the deletes of the
     dictionary (`:710-753`). Damerau-Levenshtein must be at most 1 (`:947-990`). `putIfAbsent` means a word
     that is already a prefix candidate keeps distance 0.
6. **Score each candidate:**
   - `rank = f + 0.35*prefix - {0:0, 1:0.20, else 0.50}[dist] - 0.18*|len(w)-len(in)|/len(in) - (0.30 if len(in) >= 5 && len(w) <= 3)`,
     where `f = freq/maxFreq` (`:992-1005`);
   - `rank *= clamp(0.60 + w, 0.60, 1.60)` (`MixedLanguageScoringPolicy.kt:46-49`);
   - `conf = clamp(0.55*f + 0.25*{0:1, 1:0.75, else 0.5}[dist] + 0.15*prefix + 0.05*clamp(1-|Δlen|/len, 0, 1), 0.05, 1)` (`:1045-1062`);
   - `conf = clamp(0.88*conf + 0.12*w, 0.05, 1)` (`MixedLanguageScoringPolicy.kt:51-53`);
   - across languages, keep the entry with the higher rank (`:320-331`).
7. **Personal boost.** If the previous word has learned bigrams: `rank += 0.30*s`, `conf += 0.10*s`, where
   `s = count/(2+count)` (`:1139-1162`, `PersonalNgramStore.kt:116-120`).
8. **Sort** by rank descending, then frequency descending, then word ascending, and keep 8 (`:346-352`). Only the
   top candidate can be `isEligibleForAutoCommit` (`:370-389`, section 3).
9. **Case.** If the input is all caps the candidate becomes all caps; if the input starts with a capital, the
   candidate is title-cased (`:1353-1366`).
10. **E-mail merge.** Remembered e-mail candidates go in after the first word candidate, or first in e-mail
    fields (`:395-400,1172-1220`).

Consequences, all checked with the offline re-implementation on the bundled lists:

- A known typed word is always first, because it gets the prefix boost and no length penalty. Single letters
  are dictionary words (`h` in en, `d` in nl), so `c` gives `c, can, car, …` (nl+en).
- Completions come shortest first: `con` gives `con, connor, contact, concert, …`, and `ver` gives
  `ver, very, vera, …`.
- Three-letter typos never get corrections: `hte` gives an empty list, and `teh` gives `tehuis, tehran`.
- `blockPossiblyOffensive` (default true, `AppPrefs.kt:747-750`) and `isPrivateSession` only affect the cache
  key (`:246-247,1244-1245`). No offensive filter is implemented.
- The user dictionary is only used as an exact-match veto (`:282-284,1251-1260`). Its words are **never added
  as suggestion candidates** in `suggest()`. They are used only in `spell()` (`:200-203`).

### 2.2 Next-word algorithm (`suggestNextWordCandidates`, `LatinLanguageProvider.kt:771-884`)

- It runs only when `rawInput` is blank (`:259-266`). It returns **empty** if `textBeforeSelection` is blank or
  does not end in whitespace or one of `.,;:!?` (`:779-781,886-889`). There are no predictions at the start of a
  field.
- Tokens come from `textBeforeSelection` (at most 256 chars), split into letters plus internal `' ’ -`
  (`:891-915`).
- **In-field repeats.** For each earlier occurrence of `prev1`, its follower gets +3, plus +6 if `prev2` matches
  as well. This is multiplied by `clamp(0.5 + w, 0.5, 1.5)` and only counts if the follower is in that language's
  model (`:801-822`). The loop runs per language, so a word in both models is counted twice.
- **Personal n-grams.** Only for the primary language: `+6 + 8*score/maxScore` (`:826-844`). In the model,
  trigram counts are weighted ×3 (`PersonalNgramStore.kt:87-110`).
- If there are no scores, a **fallback** applies: the top 64 most frequent words of length 2 or more for each
  language, except `prev1` (`LatinPredictionShortcuts.kt:55-66,87-93`). Rank is
  `clamp(0.20 + 0.35*f, 0.10, 0.55) * (0.5 + w)` (`LatinLanguageProvider.kt:1007-1043`). In practice this gives
  `ik, je, you, het, …` (nl+en) whatever the context.
- Confidence is `0.25 + 0.55*score/max + 0.20*freq/maxFreq` (`:865-883`). Next-word candidates are never
  auto-commit.

### 2.3 Assembling candidates (`NlpManager.kt:197-236,310-333`)

- **Emoji.** Emoji suggestions are computed first. If any exist and the emoji type prefix is non-empty, word
  suggestions are dropped (`NlpManager.kt:200-225`). The default type is `LEADING_COLON` (`AppPrefs.kt:308-311`),
  and the query must start with `:` and be at least 3 chars long (`EmojiSuggestionProvider.kt:124-138`,
  `AppPrefs.kt:320-323`). Emoji has no effect on benchmark text.
- **Clipboard.** The clipboard provider **replaces** all candidates, using `.ifEmpty { internal }`, but only when
  the field text is blank and the clip is less than 60 s old (`NlpManager.kt:312-326,403-411,481-490`;
  `AppPrefs.kt:101-108`). The harness should not use the clipboard to clear the field.
- **Inline autofill** (API 30+) replaces the whole `CandidatesRow` when present (`Smartbar.kt:140-144,283-287,402-405`),
  default on (`AppPrefs.kt:735-738`).

### 2.4 Display (`CandidatesRow.kt:62-137`)

- The order is list order, **left to right**. The top candidate sits in the leftmost slot (`:80-85,105-134`). A
  single candidate is centred (`:80-84`).
- `CLASSIC` shows the first 3 in equal-weight slots (`:94-96,101-102`). `DYNAMIC` and `DYNAMIC_SCROLLABLE` show
  all of them (up to 8), each with wrap-content width up to 160 dp, and the latter can scroll horizontally
  (`:77-79,97-99,103`). The default is `DYNAMIC_SCROLLABLE` (`AppPrefs.kt:743-746`), and it can be set in
  Smartbar settings (`K/app/settings/smartbar/SmartbarScreen.kt:52`). Default layout is
  `SUGGESTIONS_ACTIONS_EXTENDED`: an actions toggle, then the candidates, then a sticky action
  (`AppPrefs.kt:681-684`, `Smartbar.kt:432-441`). How many candidates are visible depends on word widths. For the
  "3 visible" protocol, set `CLASSIC` or read the visible slots from the UI dump.
- The literal typed word is **not added specially**. It shows only if it is a dictionary word, and then it is
  in slot 1. An unknown literal is never offered.
- An auto-commit candidate gets the `auto-commit=1` style attribute, usually bold (`CandidatesRow.kt:155`).

### 2.5 Tapping a suggestion

1. `CandidatesRow` `onClick` calls `keyboardManager.commitCandidate(candidates[n])` (`CandidatesRow.kt:119-122`).
2. `commitCandidate` records metrics and clears pending autocorrect undo for manual commits
   (`KeyboardManager.kt:316-330`). It then starts `notifySuggestionAccepted` asynchronously (`:331-333`, ungated
   by incognito) and calls `editorInstance.commitCompletion` (`:334-337`).
3. `commitCompletion` (`EditorInstance.kt:341-367`) handles two cases:
   - With a composing word, it replaces the word through `finalizeComposingText`
     (`AbstractEditorInstance.kt:425-457`). It arms a **phantom space**, not a real space, when nothing follows
     the cursor. If a space already follows, it moves the cursor past it
     (`AcceptedSuggestionSpacingPolicy.kt:31-47`).
   - Without one (next-word), it commits the text, with a leading space if a phantom space was pending, and arms
     a new phantom space.
4. The phantom space becomes a real space only when the next letter, digit or opening symbol is typed
   (`EditorInstance.kt:648-659,291,301`). A typed space after a tap is not doubled: space is not in
   `symbolsFollowingPhantomSpace` (`l10n/extension.json:16-24`). In the benchmark this means the field reads
   `"word"`, not `"word "`, until the next tap.
5. Next-word refresh comes from the editor's update of the commit. Composing is suppressed while the phantom
   space is active (`EditorInstance.kt:177-180`), then `PendingSeparatorSuggestionContent` adds a virtual space
   and next-word prediction runs (`KeyboardManager.kt:259-263`).

---

## 3. Autocorrect

### 3.1 Trigger points

The auto-commit candidate is `activeCandidates.firstOrNull { it.isEligibleForAutoCommit }`
(`NlpManager.kt:283-285`). It is committed with `CandidateCommitOrigin.AUTO_COMMIT` in these cases:

- the space key (`KeyboardManager.kt:637-639`), and a hardware space (`:621-623`);
- any character or numeric key whose first code point is **not alphabetic**: punctuation, `'`, digits
  (`KeyboardManager.kt:903-909`). For example, `shouldn` followed by `'` may be autocorrected before `'t`;
- any key in MEDIA (emoji) mode (`KeyboardManager.kt:874-877`).

It is **not** triggered by Enter or the IME action (`KeyboardManager.kt:530-551`), by backspace, or by a cursor
move.

**Staleness.** `activeCandidates` is whatever async result came last (sections 1.3 and 2.3). If space arrives
before the result for the last letter, the candidate list (and its auto-commit flag) belongs to the previous
prefix. `commitCompletion` still replaces the full current composing word (`EditorInstance.kt:345-352`). Fast
adb typing makes this more likely. The harness should wait at least the p95 suggestion latency between taps,
and the simulator should note the assumption.

### 3.2 Eligibility (`HighCertaintyAutocorrectPolicy.kt:30-51`) and effective config

The policy rejects a candidate when any of these hold:

- autocorrect is disabled;
- the input is an exact word in **any** subtype language or in the user dictionary (`LatinLanguageProvider.kt:282-284`);
- the input is in the never-correct list;
- `len < minInputLength`, or the candidate equals the input;
- the edit distance is outside `[1, maxAutoCorrectEditDistance = 1]`;
- `confidence < minConfidence`;
- `confidence - runnerUpConfidence < minConfidenceGap`.

It is applied only to the top-ranked candidate (`LatinLanguageProvider.kt:374-382`). **Prefix completions have
distance 0 and can never auto-commit.**

Defaults (`AppPrefs.kt:169-214`), clamped in `LatinLanguageProvider.kt:1080-1089`:

| Pref | Default | Clamp | In UI? |
|---|---|---|---|
| `correction.highCertaintyAutocorrectEnabled` | true | – | no |
| `…MinConfidencePercent` | 88 | 50..99 | no |
| `…MinConfidenceGapPercent` | 12 | 0..50 | no |
| `…MinInputLength` | 4 | 3..12 | no |
| `correction.appSpecificAutocorrectProfilesEnabled` | true | – | yes (`K/app/settings/typing/TypingScreen.kt:142`) |
| `…ChatAggressivenessPercent` | 112 | 70..130 | yes |
| `…EmailAggressivenessPercent` | 88 | 70..130 | yes |
| `correction.autoCapitalization` | true | – | yes |
| `correction.autoSpacePunctuation` | true | – | yes |
| `correction.doubleSpacePeriod` | true | – | yes |
| `suggestion.enabled` | true | – | yes |
| `suggestion.displayMode` | DYNAMIC_SCROLLABLE | – | Smartbar screen |
| `suggestion.incognitoMode` | DYNAMIC_ON_OFF | – | yes |
| `suggestion.personalizedLearningEnabled` | true | – | yes |

**App profiles** (`AppSpecificAutocorrectProfilePolicy.kt:69-113`):

- CHAT: a chat package, or `SHORT_MESSAGE`, or an IME action of `SEND`.
- EMAIL: an e-mail package, or an e-mail address or subject variation.
- DEFAULT: everything else.

Each profile divides `minConfidence`, `minConfidenceGap` and `minInputLength` by its aggressiveness factor
(`:90-104`). The effective values are:

| Profile | minConf | minGap | minLen |
|---|---|---|---|
| DEFAULT | 0.88 | 0.12 | 4 |
| CHAT | 0.786 | 0.107 | 4 |
| EMAIL | 0.99 (clamped) | 0.136 | 5 |

The benchmark field (multi-line text, harness package) is DEFAULT.

### 3.3 Why autocorrect is effectively off

- A single-language non-prefix correction reaches at most `0.88*(0.55*f + 0.2375) + 0.12`, which is 0.813 at
  `f = 1`. That is below 0.88 (formulas in 2.1).
- A prefix correction (an insertion at the end of a word outside the 48-word prefix pool) needs
  `f >= ~0.88`, which only `you`/`i` (en) or `ik`/`je` (nl) reach.
- With nl+en, `w` drops to about 0.44 to 0.56, so confidence is lower still.

Offline re-implementation (bundled lists, frequency-weighted words of length 4 or more, one random edit of the
protocol's type):

| Setup | Auto-corrections | Typo is itself a dictionary word (vetoed) | Target is top-1 | Target in top-3 |
|---|---|---|---|---|
| nl only | 0 / 600 | 80 | 388 | 478 |
| nl + en | 0 / 600 | 76 | 389 | 468 |

Example confidences: `becuase→because` 0.344 (en) and 0.276 (nl+en); `wrold→world` 0.335; `gistren→gisteren`
0.272. Check on the device with "autocorrect applied" in the Devtools debug log (`TypingSpeedMetrics.kt`,
`KeyboardManager.kt:323-327`).

### 3.4 Undo and revert

- After an auto-commit, `AutocorrectUndoTracker` stores `(original, corrected, candidate)` in memory
  (`KeyboardManager.kt:323-327`, `AutocorrectUndoTracker.kt:33-49`).
- **Backspace** (character unit), as long as the corrected token is still the last token before the cursor,
  selects that token and commits the original instead (`KeyboardManager.kt:470-493,507-509`,
  `AutocorrectUndoTracker.kt:61-70,95-127`). The trailing space stays.
- The **UNDO key** does the same (`KeyboardManager.kt:465-468,865`).
- Otherwise backspace after an accepted suggestion calls `revertPreviouslyAcceptedCandidate`
  (`KeyboardManager.kt:446-463`). With `glide.immediateBackspaceDeletesWord` (default true, `AppPrefs.kt:404-407`),
  it deletes the whole word while the phantom space is active (`EditorInstance.kt:441-444`).
- A revert of an auto-commit candidate adds the original token to the never-correct list (`NeverCorrectWords`,
  max 256 entries) (`LatinLanguageProvider.kt:474-483`, `NeverCorrectWords.kt:92,110-123`). It also suppresses
  rapid learning of that word for 24 h (`RapidPersonalVocabularyLearner.kt:37,84-93`).

---

## 4. Language handling

- `Subtype.locales()` is `[primaryLocale] + secondaryLocales` (`Subtype.kt:79-83`). The default subtype
  is `en-US` with no secondary locales (`Subtype.kt:63-73`). `SubtypeManager` falls back to it when no subtype
  exists (`SubtypeManager.kt:87`).
- The Latin provider loads a model for every locale in `preload` (`LatinLanguageProvider.kt:167-171`), scores
  all non-empty models, and weights them with `MixedLanguageScoringPolicy` (sections 2.1 and 2.2). Exact
  matches veto autocorrect in any language. Personal n-grams are read and written **only for the primary
  language** (`LatinLanguageProvider.kt:423-427,826-832,1150`).
- **Secondary locales have no editor UI.** `SubtypeEditorState` keeps `secondaryLocales` (`SubtypeEditorScreen.kt:137,148`),
  but its accessor is commented out (`SubtypeEditorScreen.kt:211`). No control sets it. Presets always produce
  `secondaryLocales = emptyList()` (`Subtype.kt:248-260`). Nothing else in `K/` writes it.
- **There are no Dutch presets.** `l10n/extension.json` lists 72 presets from line 85, and none has an `nl` tag.
  There is also no `nl` popup mapping (`popupMappings/` has no `nl.json`). After a locale is picked, the
  editor's `getSubtypePresetForLocale(nl)` returns null, so the popup mapping becomes `default`
  (`SubtypeEditorScreen.kt:225-234`, `SubtypeManager.kt:140-143`). The `nl` frequency dictionary does exist
  (`LatinLanguageProvider.kt:72-75`).
- **What a user must do today:**
  1. Settings > Languages & Layouts > Add subtype > "view all presets", and pick an English preset. This fills
     layouts, composer and currency.
  2. Tap Primary language and pick Dutch (`SubtypeEditorScreen.kt:361-373`). This is the broken tap in
     section 5.
  3. Save. Without a preset, eleven Select fields must be filled by hand (`SubtypeEditorScreen.kt:157-177`).
  4. Add a second subtype, English (US).
  5. Switch between the two subtypes manually with the language key or a swipe (`KeyboardManager.kt:557-563`).
     Each subtype is single-language.

  The only way to get nl+en *simultaneously* is to inject `secondaryLocales` into the
  `localization__subtypes` JSON pref (`AppPrefs.kt:640-643`), for example by editing a backup and restoring it.
  This is not verified. The protocol's "Dutch primary with English available at the same time" needs a code
  change: recommendation R8.

---

## 5. Bug: "Primary language" in Add subtype does nothing (beta build)

**What the code does:** the field is a `FlorisDropdownLikeButton` whose `onClick` navigates to
`Routes.Settings.SelectLocale` (`SubtypeEditorScreen.kt:361-373`). `FlorisDropdownLikeButton` does not attach a
click handler. It renders a read-only `JetPrefTextField` with its own `MutableInteractionSource` and calls
`onClick()` **from composition** when `interactionSource.collectIsPressedAsState()` reads true
(`FlorisDropdownLikeButton.kt:49-54,56-72`).

From inspecting the dependency bytecode: `JetPrefTextField` (jetpref-material-ui 0.3.0) delegates to material3
`TextField`/`OutlinedTextField` (value-based, legacy `CoreTextField`). In compose-foundation 1.10.3 that uses
`tapPressTextFieldModifier`, which emits `PressInteraction.Press` and then `Release` from coroutines launched on
pointer down and up.

**Most likely root cause (confidence ~75%): the press is never observed.** `adb shell input tap`, which the bench
helper uses (`tools/kbbench/ph.sh` `tap`/`tapt`), injects DOWN and UP about 1 ms apart. Press and Release are
then both emitted before the next frame, so `pressed` goes true and back to false between recompositions. The
composable never sees `pressed == true` and `onClick()` never runs: no navigation and no crash.

The other dropdowns use `JetPrefDropdown`, which opens on a real click, so they work. A slow human tap usually
spans several frames and works. It can even call `onClick()` on several recompositions and push `SelectLocale`
twice. The beta/R8 correlation is probably incidental: the bug is in how the click is detected, not in the build.

**Why navigation or R8 is unlikely:**

- Names are kept (`-dontobfuscate`, `proguard-rules.pro:2`).
- `INSTANCE`/`serializer()` of `@Serializable` objects are kept (`proguard-rules.pro:20-26`), and runtime
  annotations are kept (`:29`).
- `composableWithDeepLink` reads `@Deeplink` when the graph is built and would *crash at start-up* if an
  annotation were stripped (`Routes.kt:88-99,286`).
- The same `navigate(<object route>)` pattern works for about 20 other routes in the same build, for example
  `Routes.Settings.SubtypeAdd` (`LocalizationScreen.kt:95`), `Localization`, `Theme` and others
  (`K/app/settings/HomeScreen.kt:111-162`).
- A failed route match or a missing serializer would throw, which means a crash, not a silent no-op.
- `SelectLocaleScreen` itself has nothing build-type specific (`SelectLocaleScreen.kt:59-150`).

**Discriminating test** (for the device owner): on the beta build, run
`adb shell input swipe X Y X Y 300`, which is a 300 ms stationary press, on the field. If it navigates while
`input tap` does not, the hypothesis is confirmed. If neither works, check logcat for
`IllegalArgumentException … destination … cannot be found` or `Serializer for class … is not found`, and inspect
`mapping.txt`/`usage.txt` for `Routes$Settings$SelectLocale`.

**Minimal fix:** in `FlorisDropdownLikeButton.kt:49-54`, replace the composition-time check with a real click
target. Use either of these:

```kotlin
// Option A: react to Release, which is emitted even for a zero-length tap
val currentOnClick by rememberUpdatedState(onClick)
LaunchedEffect(interactionSource) {
    interactionSource.interactions.collect { if (it is PressInteraction.Release) currentOnClick() }
}
// Option B (also fixes TalkBack and uiautomator clicks): overlay inside the Box after the text field
Box(Modifier.matchParentSize().clickable(onClick = onClick))
```

Optionally, also use `navController.navigate(Routes.Settings.SelectLocale) { launchSingleTop = true }`
(`SubtypeEditorScreen.kt:369`) to prevent duplicate pushes.

---

## 6. No personalized learning, or incognito

- `activeState.isIncognitoMode` is set at `onStartInputView`. With the default `DYNAMIC_ON_OFF`
  (`AppPrefs.kt:751-754`), it equals `imeOptions.flagNoPersonalizedLearning || forceIncognitoModeFromDynamic`
  (`EditorInstance.kt:137-143`).
- **Learning writes that are blocked:** `NlpManager.notifyTextBoundary` returns early (`NlpManager.kt:244-255`).
  That blocks personal n-gram learning and e-mail address learning (`LatinLanguageProvider.kt:407-429`).
- **Prediction does not change.** `isPrivateSession` reaches the providers (`NlpManager.kt:171,207,222,319`), but
  `LatinLanguageProvider` only uses it in the cache key (`LatinLanguageProvider.kt:247,1245`). Incognito still
  **reads**:
  - personal n-grams for next-word (`:827-844`) and the in-word context boost (`:1152`);
  - remembered e-mails (`:258,1172-1194`);
  - the user dictionary veto (`:282-284`);
  - the never-correct list (`:362-365`).

  Candidate ranking is identical except for the cache partition.
- **Not gated (bug):**
  - `commitCandidate` always calls `notifySuggestionAccepted` (`KeyboardManager.kt:331-333`). That feeds
    `RapidPersonalVocabularyLearner` and can **insert or update entries in the Floris user dictionary**
    (`LatinLanguageProvider.kt:437-455,1284-1318`).
  - Reverts write the never-correct pref (`LatinLanguageProvider.kt:457-484`).

  This applies to suggestion taps, so it happens in the benchmark field. Its effect on predictions is
  negligible, because promoted words are already in the frequency list and user dictionary words are not
  candidates. It is still cross-run state and a privacy gap. Gate both on `isIncognitoMode`.
- Other incognito effects: incognito UI icons (`Smartbar.kt:236`, `K/ime/text/TextInputLayout.kt:70`) and
  cloud-AI availability (`K/ime/text/rewrite/CloudAiAvailabilityPolicy.kt:99,120`). Neither affects prediction.

---

## 7. Personal learning paths and when they write

| Store | Written when | Gating | Persistence |
|---|---|---|---|
| `PersonalNgramStore`: bigrams (6,000) and trigrams (4,000) per language, evicted at 1.2× (`PersonalNgramStore.kt:46-153`) | On a separator typed after a letter or digit (space, `.,;:!?`, Enter action) (`EditorInstance.kt:272-279,282,319,612`). It learns the last 2 or 3 tokens of the last 96 chars into the **primary language** (`LatinLanguageProvider.kt:407-429`) | `personalizedLearningEnabled`, not incognito, key variation NORMAL or EMAIL (`NlpManager.kt:244-250`) | `files/personal_learning/personal_ngrams_v1.json`, saved 5 s after a change (`PersonalNgramStore.kt:159-265,268-272`) |
| `PersonalDataStore` (e-mails, max 24) | The same boundary, when the trailing token is an e-mail address (`LatinLanguageProvider.kt:413-419`) | Same as above | `personal_data_v1.json`, saved after 3 s (`PersonalDataStore.kt:39-79`) |
| `RapidPersonalVocabularyLearner`, in memory, which promotes to the **Floris user dictionary** | On every accepted candidate, manual or auto-commit (`KeyboardManager.kt:331-333`). Promotion needs 1 confirmation if `conf >= 0.92`, 2 if `>= 0.72`, else 3, within a 12 h decay window. Words must be 3 to 32 chars (`RapidPersonalVocabularyLearner.kt:36,47-82,95-101,108-120`). The user dictionary entry gets `freq = (existing ?: 160) + min(16*confirmations, 64)` (`LatinLanguageProvider.kt:1284-1318`) | **None** (not incognito-gated) | Room user dictionary DB (`DictionaryManager.kt:111-127`) |
| `NeverCorrectWords` pref | On a revert of an auto-commit candidate by backspace or undo (`LatinLanguageProvider.kt:474-483`) | **None** | `dictionary__never_correct_words_data` (`AppPrefs.kt:262-265`) |
| User dictionaries (Floris and Android system) | Manual edits in settings, plus the promotions above. They are read for the veto and `spell()` (`DictionaryManager.kt:58-109`) | Prefs `enable…UserDictionary`, default on (`AppPrefs.kt:254-261`) | DB |

`clearPersonalizedData` wipes the n-grams, e-mails and cache, but **not** the user dictionary promotions
(`NlpManager.kt:261-267`, `LatinLanguageProvider.kt:431-435`). Run it on the phone before benchmark baselines,
because incognito still reads earlier learned n-grams.

---

## 8. Checklist for a faithful offline simulator

1. **Typing.** Map each tap to the key whose `touchBounds` contain it; first match wins (`TextKeyboard.kt:40-47`).
   No probabilistic decoding.
2. **State.** Build `textBeforeSelection` as the last 256 chars. The composing word is the ICU word before the
   cursor. After a suggestion tap there is no composing word and a virtual space applies (sections 1.2 and 2.5).
3. **Suggestions.** Use section 2.1 or 2.2 exactly, with `maxCandidateCount = 8` and the bundled
   `en_50k.txt`/`nl_50k.txt`. Build the indexes the same way: 48-word prefix pools, a delete index for the top
   20,000 words, and a 64-word fallback. Apply the cache semantics, keyed by word when composing, if strict
   fidelity matters.
4. **Visible slots.** `CLASSIC` gives the first 3. The default `DYNAMIC_SCROLLABLE` shows as many as fit (measure
   on the device).
5. **Autocorrect.** Apply it on space and on non-alphabetic characters, not on Enter (section 3.1), with the
   DEFAULT profile thresholds. Expect about zero corrections. A simulator that shows many corrections is wrong.
6. **Spacing.** A tapped suggestion gets a phantom space. Autocorrect plus space gives `"word "`
   (`EditorInstance.kt:341-367`, `KeyboardManager.kt:637-666`). Auto-capitalisation is on for capSentences fields
   (`KeyboardManager.kt:242-252`); comparison is case-insensitive.
7. **Personal state.** Assume none in the benchmark field: no n-gram writes, and a fresh store if it was cleared.
8. **Async races.** These are not modelled. The harness should pace taps above the p95 suggestion latency.
   Otherwise the device can autocorrect with a stale candidate (section 3.1).

---

## 9. Ten highest-leverage changes to beat Gboard

KSR means keystroke savings with 3 visible slots. WER means residual word error after noisy typing.

| # | Target | Where | Change |
|---|---|---|---|
| R1 | WER (largest) | `LatinLanguageProvider.calculateConfidence` (`:1045-1062`), `HighCertaintyAutocorrectPolicy.shouldAutoCommit` (`:30-51`), defaults `AppPrefs.kt:178-189` | Replace linear-frequency "confidence" and the absolute 0.88 gate with a **normalised posterior** over {literal input} ∪ candidates: `log P_lm(w|prev) + log P_err(input|w)` → softmax. Auto-commit when the top candidate is not the literal, `P >= τ` and the log-odds against the literal is at least `δ`. Tune τ and δ on `dev` against the false-correction rate. Today the target is top-1 for about 65% of typos but nothing fires (section 3.3). |
| R2 | KSR | `rankSuggestionCandidate` (`:992-1005`) | Rank by `log P(w | context)`, with no length penalty for completions. The current linear `f` plus `0.18*Δlen/len` puts the shortest completion first. Optionally order the 3 slots by `P(w)` among words with `len(w) > len(input)`. |
| R3 | KSR | `LatinPredictionShortcuts.lookupPrefixCandidates` (`:69-85`), pool size `LatinLanguageProvider.kt:65-66` | Replace the "top 48 of the 3-letter bucket" with a full prefix search: a sorted array with binary-searched range plus a top-k heap, or a trie with cached top-k per node. Every word must be reachable for every prefix. |
| R4 | KSR (largest) | `suggestNextWordCandidates` (`:771-884`), fallback (`:1007-1043`), empty-field early return (`:779-781`), `preload` (`:167-171`) | Ship a pruned, quantised static **bigram/trigram LM per language** (nl, en) trained on non-benchmark corpora, with Tatoeba excluded. Use it for next-word prediction, including sentence or field start, and to condition in-word completions on the previous 1 or 2 words. Next-word top-3 is currently a fixed unigram list. |
| R5 | KSR | Post-sort selection (`LatinLanguageProvider.kt:346-389`) or `NlpManager.assembleCandidates` (`:310-333`); display `AppPrefs.kt:743-746`, `CandidatesRow.kt:101-104` | Do not spend a visible slot on the exact typed word when no autocorrect is pending, since space commits it anyway and tapping it saves no tap. Show the literal only when an autocorrect would replace it (Gboard-like, outside knowledge). Make 3 fixed slots the default (`CLASSIC`). |
| R6 | WER | `MaxEditDistance`/`MinLengthForTypoCorrections` (`LatinLanguageProvider.kt:59-61`), `lookupCorrections` (`:710-753`), key geometry hook (`TextKeyboardLayout.kt:157-160`) | Add a **keyboard-aware error model**: substitution cost from QWERTY key-centre distance, plus cheap transposition, omission and doubling (the protocol's cognitive errors). Use edit distance 2 for length 6 or more. Correct 3-letter words with a stricter threshold. Later, feed raw tap coordinates (section 1.5). |
| R7 | WER | Dictionaries `en_50k.txt`/`nl_50k.txt`; exact-match veto `LatinLanguageProvider.kt:282-284`, `HighCertaintyAutocorrectPolicy.kt:40` | The OpenSubtitles lists contain typos and fragments (`helo`, `hep`, `im`, `dont`, single letters). Every one vetoes autocorrect, and the nl+en union doubles the veto set: 76 to 80 of 600 typos are vetoed outright. Use a curated lexicon intersected with frequencies. Treat rare exact matches as weak, so a much more probable neighbour in context can still win (real-word errors). Map `dont`→`don't` and `een`→`één` as canonical forms. |
| R8 | Both (mixed nl+en) | `l10n/extension.json:85`; `SubtypeEditorScreen.kt:211`; cache key `LatinLanguageProvider.kt:1231-1236`; `MixedLanguageScoringPolicy.kt:26-53` | Add an `nl-NL` preset and editor UI for secondary locales, or default nl+en for Dutch. **Fix the cache key** so context-dependent language weights and boosts are not reused across contexts. Tune the priors and context weights on mixed `dev` sentences. Keep per-language LM context, with a sentence-level language posterior. |
| R9 | WER (correctness) | `NlpManager.getAutoCommitCandidate` (`:283-285`), `suggest` (`:197-236`), `KeyboardManager.handleSpace` (`:637-666`), `handleEnter` (`:530-551`), `onInputKeyUp` (`:903-909`) | Store the input string with each candidate list. At space, if it does not match the current composing word, compute synchronously within a time budget. Autocorrect on Enter and the IME action too. Do not autocorrect on digits or on `'` inside a word. Cancel superseded suggest jobs, for example with a `collectLatest`-style job per request. |
| R10 | WER (beyond Gboard) | `KeyboardManager.handleSpace` (`:637-666`), with range tracking like `AutocorrectUndoTracker.kt:106-127` | **Right-context revision:** when word *n* is committed, re-score word *n−1* with `P(w_{n-1} | w_{n-2}) · P(w_n | w_{n-1})` and replace it if the posterior flips with a high margin. Never do this for words the user explicitly accepted or reverted. WER is scored on final text and the noisy typist never backspaces, so this directly removes residual errors that per-word decisions miss. |

Before baselines, also gate `notifySuggestionAccepted` and the never-correct writes on incognito (section 6),
and clear personalised data on the device (section 7). That keeps runs independent.
