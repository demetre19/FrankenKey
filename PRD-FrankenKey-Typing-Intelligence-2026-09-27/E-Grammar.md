# Lane E Handoff PRD — Offline Grammar Rules and AI "Fix grammar"

Date: 2026-09-27
Lane: E
Status: Both features approved by the user (2026-09-27). Q5 (overview §3) has a default.
Depends on: nothing. **Fully parallel** with every other lane from day one.
Shared-file contact: `Keyboard2.java` (the grammar section only), `res/xml/settings.xml`, strings, and `AGENTS.md` lines. All merges are textual.
Read first: `00-Overview-and-Philosophy.md` (principles 2, 3, 7, 9; §4, §7.4).

## 0. Handoff essentials

| Item | Value |
|---|---|
| Source checkout (Mac mini) | `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect` |
| Base / branch / worktree | `57f1356` / `ti/lane-e` / `../FrankenKey-ti-e` |
| DOX chain | source `AGENTS.md` → `srcs/AGENTS.md` → `srcs/juloo.keyboard2/AGENTS.md`; `assets/AGENTS.md` if the rules data lives in assets; delivery `AGENTS.md` (Reader AI line) |
| New files | `grammar/GrammarRules.java`, `grammar/GrammarRule.java` (+ one class per rule family), `grammar/GrammarCoordinator.java`, `grammar/AiGrammarFixer.java`, `grammar/GrammarDiffView.java`, `grammar/AGENTS.md`; test fixtures `test/resources/grammar/*.tsv` |
| Touched files | `Keyboard2.java` (lines ~484–700: `grammar_correction_changed`, `show_grammar_correction`, `apply_grammar_correction`), `SystemGrammarChecker.java` (merge only), `ReaderAiAction.java` (+`FIX_GRAMMAR`), `ReaderAiButtonController`/dispatchers, `ReaderAiSettings.java` (writing model, disclosure v4), `ReaderAiOpenRouter.java` (reuse `buildRequest`/`parseCompletion`), `ReaderPageCaptureService.java` (floating button: unavailable message), settings and strings |
| Tests | `SystemGrammarCheckerTest`, `ReaderAiButtonTest`, `ReaderAiContractsTest`, new `GrammarRulesTest` (fixture-driven), `GrammarCoordinatorTest`, `AiGrammarFixerTest`, `GrammarDiffViewTest` |

## 1. Objective

1. **Offline grammar rules.** Deterministic, high-precision, on-device checks that catch the common mistakes spell-check misses (`your welcome`, `could of`, `a apple`, `the the`, `its a`, `more then`). They're offered as a one-tap fix in the assistant strip and never applied silently.
2. **AI "Fix grammar".** An explicit omnibutton action that sends the current field text, or the selection, to the user's OpenRouter model after a disclosure. It shows a clear before/after preview and replaces the text only when you tap Replace.

## 2. Current behavior (2.0.119)

- `SystemGrammarChecker` uses the platform `SpellCheckerSession.getSentenceSuggestions`:
  - `MAX_TEXT_LENGTH = 500`, `REQUEST_DELAY_MS = 350`, `REQUEST_TIMEOUT_MS = 1500`;
  - it submits only the latest completed sentence after `.`, `!`, or `?`;
  - it's off by default (`grammar_corrections`);
  - quality depends on the phone.
- A correction is shown in `AssistantStripView` as "Change to X?" with Apply/Dismiss (`Keyboard2.java:484-700`). `Correction.apply` revalidates the cursor and the text suffix before replacing.
- Reader AI already has an encrypted user key, model selection (`openrouter_model_id`, default `inception/mercury-2.5`), disclosure (`disclosure_accepted_v3`), and a bounded HTTP client.

## 3. Functional requirements

### E-F1 Offline rule engine (`GrammarRules`)

- Pure Java, stateless, with no allocations beyond the result list.
- Input: `(sentence, locale)`, where the sentence is ≤500 UTF-16 characters. Output: an ordered list of `GrammarIssue {ruleId, offset, length, replacement, message}`.
- Tokenization: simple word/punctuation tokens with offsets. Matching is case-insensitive, and the replacement keeps the source casing (`Your welcome` → `You're welcome`).
- **Rule families (v1)**. Each rule has a positive and a negative fixture table. A rule may ship only if it reaches **≥98% precision on its negative table and 100% on its positive table**.

  | Rule ID | Pattern (high-precision form only) | Replacement | Key negatives |
  |---|---|---|---|
  | `DOUBLE_WORD` | the same word twice in a row (excluding "had had", "that that", "is is"-in-quotes, and numbers) | a single word | "had had", "that that" |
  | `A_AN` | `a` + a vowel-sound word, or `an` + a consonant-sound word | `an`/`a` | exception lists: hour, honest, honour/honor, heir → an; university, unicorn, user, one, once, European, eulogy → a; initialisms by letter name (an FBI, an MBA, a URL) |
  | `COULD_OF` | `could/should/would/must/might of` + (non-noun) | `… have` | "would of course" (skip when followed by "course") |
  | `YOUR_YOURE` | `your` + {welcome, going, not, being, the, a, right, wrong, so, very, too} | `you're` | "your right hand", "your welcome pack" (skip when a noun follows the adjective; v1 uses an explicit stop-list) |
  | `ITS_ITS` | `its` + {a, the, been, going, not, time, ok, okay, fine, over, all} → `it's`; `it's` + {own} → `its` | as shown | "its own" stays |
  | `THEIR_THERE` | `their` + {is, are, was, were, will be, has been} → `there` | `there` | "their is-" (rare, accepted) |
  | `THEN_THAN` | comparative (`-er` word or more/less/rather/other/better/worse) + `then` | `than` | "and then", "since then" |
  | `ALOT` | `alot` | `a lot` | — |
  | `SPACE_DOUBLE` | two or more spaces between words, in prose only | one space | code-like lines (contain `{`, `;`, or backticks) skipped |

- **Not duplicated.** Before adding a rule, check the existing decoder repairs: contractions/apostrophes, `im`→`I'm`, `i`→`I`, and space-before-punctuation all already exist. The lane report lists each overlap checked.
- **Rule data.** Word lists live in `assets/grammar/en.json`, versioned and loaded once on a background thread. Code must not hardcode long phrase lists (consistent with the existing "code must not hardcode phrases" rule for suggestions).

### E-F2 Coordinator and presentation (`GrammarCoordinator`)

- **Triggers:** the same trigger points as the system checker (a sentence completed with `.`, `!`, or `?`), plus the IME action key (Send, Go, Done) for the last unfinished sentence.
- **Execution:** runs on the grammar background handler. Latest wins: a newer sentence cancels an older pending check.
- **Merging:** issues from `GrammarRules` and `SystemGrammarChecker` are combined. Identical `(offset, length, replacement)` entries are deduplicated. Offline issues come first.
- **Presentation:** the existing `AssistantStripView` prompt, one issue at a time:
  - Message: `"your welcome" → "you're welcome"`. Buttons: **Fix**, **Ignore**, and a small **⋯** that opens "Turn off this rule".
  - A queue of at most 3 issues per sentence; the next one appears after Fix or Ignore.
- **Apply:** reuse `SystemGrammarChecker.Correction.apply` semantics (cursor match plus exact suffix revalidation). If revalidation fails, drop the issue silently; that's correct here because it isn't a tap on a visible candidate.
- **Undo:** after Fix, the strip shows **Undo** for 5 s (principle 2). Undo restores the original span if the text still matches.
- **Ignore:** the same `(ruleId, matched text)` isn't shown again in this editor session.
- Keyboard input continues normally while the prompt is visible, and typing does not dismiss it until the next sentence.

### E-F3 Settings (Smart typing → Grammar)

| Title | Key | Default | Summary |
|---|---|---|---|
| **Offline grammar fixes** | `ti_grammar_offline_rules` | **ON** | "Checks each sentence on your phone for common mix-ups like your/you're, could of, or a/an, and offers a one-tap fix. Nothing leaves your phone." |
| **Phone's grammar service** | existing `grammar_corrections` | OFF (unchanged) | existing text, moved into this section |
| **Grammar rules** | `ti_grammar_rule_<id>` | all ON | a sub-screen with one switch per rule, with a one-line example each |
| **AI writing model** | `ti_ai_writing_model` | follows the Reader AI model (Q5) | "Model used by Fix grammar. Uses your OpenRouter key." |

### E-F4 AI "Fix grammar" action

- **Catalog:** add `FIX_GRAMMAR("fix_grammar", R.string.reader_ai_action_fix_grammar)` to `ReaderAiAction`. It can be assigned to any omnibutton gesture. It isn't assigned by default, and the Settings Omnibutton row description mentions it. (Q: the user may want it on a default gesture; recommended: leave defaults unchanged and suggest *up-left*, which duplicates *tap = Chat* today.)
- **Availability** (keyboard omnibutton):
  - Allowed in eligible prose editors (overview §7.4).
  - Refused, with a toast naming the reason, in password, numeric, phone, email, URI, terminal, or unreadable editors.
  - From the **floating** omnibutton, the action shows "Open the keyboard in a text field to fix grammar" and does nothing else (v1).
- **Input:** the selection if non-empty, otherwise the whole field text through `getExtractedText` (limit **4,000** UTF-16). Longer text → refuse with "Select up to 4,000 characters to fix". Keep `{text, selectionStart, selectionEnd, extractedStartOffset}` plus the SHA-256 of the text as the revalidation snapshot.
- **Disclosure:** bump to `disclosure_accepted_v4`. The v4 text adds: "Fix grammar sends the text in the current field (or your selection) to OpenRouter and the model you picked, only when you tap it." Users who accepted v3 see the v4 disclosure once, the first time they use Fix grammar. Other Reader AI actions keep working under v3.
- **Key:** the existing encrypted user key. With no key, the action opens AI settings.
- **Request:**
  - model `ti_ai_writing_model`, or else `openrouter_model_id`, or else `PREFERRED_MODEL_ID`;
  - system prompt (fixed, stored in `assets/grammar/ai_prompt.txt`): "Correct spelling, grammar, and punctuation. Keep the meaning, tone, language, formatting, line breaks, emoji, @mentions, #tags, URLs, and names unchanged. Do not add or remove content. Return only the corrected text.";
  - temperature 0; `max_tokens` about 1.5× the input tokens;
  - timeout 20 s; cancellable.
- **Response handling:**
  - Strip any wrapping quotes or code fences.
  - If the response is empty, identical to the input, or more than 1.5× or less than 0.5× the input length → show "No changes suggested" or "The model's answer didn't look like a correction", with no Replace button.
- **Preview** (`GrammarDiffView`, a panel above the keyboard with the same host as the Reader quick surface):
  - A word-level diff: removed words struck through in red, added words in the accent color. Text is scrollable, and the whole panel stays within safe insets.
  - Buttons: **Replace**, **Copy**, **Cancel**.
  - Accessibility: the description lists the changes ("changed 'your' to 'you're'; …").
- **Replace:**
  - Revalidate that the field text and selection still match the snapshot hash.
  - If they match, replace the exact range in one batch edit: `setSelection(range)` + `commitText(corrected)`, then restore the cursor to the end of the replaced range.
  - If they don't match: "The text changed. Run Fix grammar again", with no edit.
  - After Replace, show **Undo** for 5 s, which restores the original text if the field still equals the corrected text.
- **Privacy:**
  - No caching of the request or response on disk, and nothing added to the Reader AI saved library.
  - Nothing is logged except the HTTP status and the model id.
  - Fields marked no-personalized-learning are allowed (it's an explicit action), but the preview header shows "This app asked for private typing".
- **Errors:** the full provider error text appears in the panel (consistent with the Reader AI quick surface contract).

## 4. Implementation stages

| Stage | Content | Parallel? | Exit |
|---|---|---|---|
| **E1** | `GrammarRules` engine, rule families, assets data, fixture tables (≥40 positives and ≥80 negatives per family) | **Immediately** | `GrammarRulesTest` green; precision table in the lane report |
| **E2** | `GrammarCoordinator`, merge with the system checker, strip prompt with Fix/Ignore/Undo/⋯, settings and rule switches | After E1 | `GrammarCoordinatorTest` green; the existing `SystemGrammarCheckerTest` unchanged and green |
| **E3** | `FIX_GRAMMAR` catalog entry, eligibility, disclosure v4, writing-model setting, request build/parse (mocked HTTP) | **Parallel with E1/E2** | `AiGrammarFixerTest`, `ReaderAiButtonTest`, `ReaderAiContractsTest` green |
| **E4** | `GrammarDiffView`, Replace/Copy/Cancel, revalidation, Undo | After E3 | `GrammarDiffViewTest` green; revalidation tests green |
| **E5** | Emulator proof (Messages-style field, a WebView textarea, a selection in a long note), live OpenRouter smoke test with the user's key **only if the user authorizes it** | After E2 and E4 | Screenshots and the lane report |

## 5. Tests (minimum)

- **Rules:** fixture-driven for each family; casing preserved; offsets correct with emoji and surrogate pairs; nothing flagged in code-like lines.
- **Coordinator:** dedupe with the system checker; queue of 3; Ignore suppresses repeats for the session; Undo restores; revalidation failure drops the issue.
- **AI:**
  - eligibility per editor class;
  - 4,000-character limit;
  - disclosure v4 gating;
  - no-key path;
  - response sanitization (quotes, fences, length ratio);
  - the hash mismatch path makes no edit;
  - Replace + Undo;
  - nothing written to the Reader AI cache or store (assert the database and prefs are untouched).
- **Throughput:** rules on a 500-character sentence ≤2 ms p95 on the emulator.

## 6. DOX and contract updates

- `srcs/juloo.keyboard2/AGENTS.md`, the grammar bullet: add "Offline grammar rules (default on) run locally on the latest completed sentence and on the IME action, and are offered one at a time with Fix/Ignore/Undo; they never auto-apply."
- `srcs/juloo.keyboard2/AGENTS.md`, the Reader AI bullets, **and** the delivery `AGENTS.md` "Reader AI release artifacts" line: add "the current field text or selection (≤4,000) sent only through the explicit Fix grammar action after the v4 disclosure, previewed as a diff, and replaced only after revalidation; never cached or saved".
- `PRODUCT.md` Product Purpose: extend the Reader AI sentence to mention Fix grammar under the same explicit-use rule.
- New `grammar/AGENTS.md` (Purpose, Ownership, Local Contracts, Work Guidance, Verification, Child DOX Index), indexed from `srcs/juloo.keyboard2/AGENTS.md`.

## 7. Risks

| Risk | Mitigation |
|---|---|
| False positives annoy users | ≥98% precision gate per rule; per-rule switches; never auto-applied |
| The AI changes meaning or content | Strict prompt, length-ratio guard, a visible diff before Replace, Undo |
| Privacy expectations | Explicit action only, v4 disclosure, no caching, refused in sensitive fields |
| Replacing text in fields that don't support selection APIs | Hash revalidation; if `setSelection` fails, fall back to Copy-only with a message |

## 8. Changelog copy

- New: offline grammar fixes. FrankenKey spots common mix-ups like your/you're, could of, and a/an, and offers a one-tap fix. It runs on your phone.
- New: "Fix grammar" for the omnibutton. It sends the current text (or your selection) to your chosen AI model, shows the changes, and replaces the text only when you tap Replace.

## 9. Done definition

E1–E5 have met their exit criteria, DOX (including the new `grammar/AGENTS.md`) is updated, the lane report is written, and the branch is merged by the integrator (at any wave, since it's independent).
