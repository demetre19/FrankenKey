# Lane C Handoff PRD — Adaptive Learning (Six Optional Behaviors)

Date: 2026-09-27
Lane: C
Status: Approved by the user (2026-09-27): backspace undo and automatic learning (overview "step 3"), and behaviors 1–6 as **optional**, each with an explanation of why you'd use it. Default values follow open question Q2.
Depends on: A0 (hooks and roles) and A1 (tickets) for C3 and C4 only. C0, C1, C2, C5, and C6 can start as soon as A0 lands; C0–C2 can start before A0 under the restriction in overview §5.5.
Blocks: D3 and D1b (both need C1's undo window).
Parallel with: A1–A4, B, D1a, D2, D4, E.
Read first: `00-Overview-and-Philosophy.md` (principles 2–6; §4, §7).

## 0. Handoff essentials

| Item | Value |
|---|---|
| Source checkout (Mac mini) | `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect` |
| Base / branch / worktree | `57f1356` / `ti/lane-c` / `../FrankenKey-ti-c` |
| DOX chain | source `AGENTS.md` → `srcs/AGENTS.md` → `srcs/juloo.keyboard2/AGENTS.md` → `suggestions/AGENTS.md`; delivery `PRODUCT.md` |
| Owned files | `suggestions/PersonalizationStore.java`, `suggestions/Decoder.java` (ranking/eligibility), the learning paths in `suggestions/SharedDecoder.java`, the undo window and backspace in `KeyEventHandler.java` (`PendingReplacement`, `commit_correction`, backspace path), `LearnedWordsActivity.java` tabs (Auto-learned, Blocked), new `suggestions/ContactNamesIndex.java`, `suggestions/LearningPolicy.java` |
| Tests | `SuggestionPersonalizationTest`, `SharedDecoderTest`, `AutocorrectScoringTest`, `KeyEventHandlerLearningContractTest`, `KeyEventHandlerAutocorrectContractTest`, `FleksyLearningGestureTest`, `LearnedWordsActivityTest`, plus new `LearningPolicyTest`, `BackspaceUndoTest`, `PassiveVocabularyTest`, `ContactNamesIndexTest`; replay scripts |
| Hard rule | **With every C switch OFF, behavior equals 2.0.119.** All existing learning and autocorrect contract tests must pass unchanged with the switches OFF. |

## 1. Objective

Bring the keyboard's self-learning up to the level users expect from mainstream keyboards without bringing back the 2026-07 "learned typos" problem. It learns from repeated, unedited use across sessions. Every automatic change is undoable. Everything learned is visible and deletable. Each behavior has its own switch with a one-sentence explanation.

## 2. Background and why the current policy exists

- 2026-07-07 investigation (`CHANGELOG-learned-suggestions-unlearn-2026-07-07.md`): passive learning taught typos, and there was no per-word unlearn. The fix was an explicit-only policy (`PRODUCT.md` Design Principles; `suggestions/AGENTS.md` "Credential-protected vocabulary…"; `srcs/juloo.keyboard2/AGENTS.md` "Vocabulary learning requires…").
- The current result is safe but tiring. New names and slang need a manual Teach or a dialog on the third use. Backspace after an autocorrect never brings back what you typed. Personal word pairs are never learned.
- This lane keeps the safety properties (bounded, reversible, no single-session evidence, and corrections never learned as vocabulary) and removes the friction.

## 3. The six behaviors (Settings → Smart typing → Learning)

Every switch's summary text **is** the "explanation of why", and it's shown as-is. Keys use the `ti_learn_` prefix.

| # | Switch title | Key | Default (Q2) | Summary / explanation shown to the user |
|---|---|---|---|---|
| 1 | **Undo autocorrect with backspace** | `ti_learn_backspace_undo` | ON | "If FrankenKey corrects a word you meant, press backspace right away to get your original word back. It then stops making that correction. Turn off if you want backspace to always delete." |
| 2 | **Learn words as I type** | `ti_learn_auto_words` | ON | "Words that aren't in the dictionary, like names, slang, or work terms, are remembered after you use them on a few different occasions. They're suggested first and only autocorrected-to after lots of use. Turn off to learn only the words you teach." |
| 3 | **Tap your typed word to keep it** | `ti_learn_typed_word_tap` | ON | "Your exact typing is shown in quotes in the suggestion bar. Tap it to keep it uncorrected, and tap again to add it to your dictionary. Turn off to use the book button instead." |
| 4 | **Hold a suggestion to remove it** | `ti_learn_long_press_remove` | ON | "Press and hold any suggestion to stop it from being suggested or used as a correction. Useful for typos that were learned or words you never use." |
| 5 | **Predict my next word** | `ti_learn_next_word` | ON | "Learns word pairs you use often, like your sign-off or your team's name, and suggests the next word after you press space. Stored only on this phone." |
| 6 | **Suggest contact names** | `ti_learn_contacts` | **OFF** | "Lets FrankenKey read the names in your contacts so it suggests them and never 'corrects' them. Needs Contacts permission. Names are never stored or shared." |

Below the switches, one non-switch row reads **"What FrankenKey has learned"** and opens Learned Words.

## 4. Functional requirements

### C-F1 Backspace undoes the last automatic change (behavior 1)

- **Undo window opens** right after any of these, as long as the cursor sits immediately after `target + separator` and nothing else has changed:
  - an automatic separator autocorrect (`commit_correction` via the separator path);
  - a suggestion tap that replaced the typed word (`stage_pending_replacement`);
  - a D1b shortcut expansion.
- **Undo window closes** on:
  - any key other than backspace;
  - cursor or selection movement;
  - a candidate tap;
  - a field or connection change;
  - the late-correction window committing that word;
  - a 5 s idle timeout.
- **First backspace inside the window** (registered as a `BackspaceHook` through A0):
  1. Verify that the text before the cursor equals `target + separator`, reusing `pending_replacement_matches_editor`.
  2. Replace it with `source`, **without** the separator, in one batch edit.
  3. Show the strip with `source` (as `ENTERED_TEXT`), then `target`, then the next alternatives. This means the user can re-pick without retyping.
  4. Record a **veto** for `(previousWord, source → target)` (C-F1a).
  5. Mark `source` as "kept literal" for this instance, so the next separator does not re-correct it.
  6. Close the window, so the next backspace deletes normally.
- **Existing plumbing to reuse:** `PendingReplacement` already stores `source`, `target`, `separator`, `cursor`, and an unused `undoToken` and `learnSourceOnUndo` (`KeyEventHandler.java:126-152`). They currently have no consumer. C1 is that consumer.
- **Late-spellcheck repairs** of earlier words (the ≤48-request retained queue) are *not* undoable by this backspace. Only the most recent change is. Late repairs keep the existing settle semantics.
- **C-F1a Correction vetoes:**
  - Store: `typing_model_correction_vetoes`, a map from `normalize(source)→normalize(target)` to `{count, lastDay, contexts: up to 4 previous words}`, capped at 512 (evict oldest `lastDay`).
  - **Effect in the decoder:**
    - A veto whose `previousWord` matches the context → never auto-apply `source → target` in that context.
    - Any 2 vetoes → never auto-apply `source → target` anywhere.
    - The target stays **suggestible**; only automatic application is blocked.
  - A veto also counts as one observation of `source` for C-F2 (the user insisted on the word).
  - Vetoes appear in Learned Words → **Blocked corrections**, where each can be deleted.
- **OFF state:** today's behavior. Backspace settles accepted corrections and never restores the original. No vetoes are recorded.

### C-F2 Automatic word learning with tiers (behavior 2)

- **Observation.** A committed word counts once when all of these hold:
  - it's committed in a safe prose editor (overview §7.4) where `should_use_personalization` is true;
  - it's `is_learnable`, not recognized by Cdict, Hunspell, or contact names, and not already taught;
  - it was **not edited afterwards**: no backspace reached into the word before the next word was committed, and no suggestion replaced it;
  - it isn't an email, URL, or path token (contains `@`, `://`, `www.`, or `/`), isn't all digits, and is 2–32 code points long;
  - it was typed, not pasted (only IME-typed characters form a word; the `CurrentlyTypedWord` revision covers this).
- **Evidence** is kept per word as `{uses, sessions (distinct session-day ids), firstDay, lastDay}`. There is at most **one use counted per session**: the first commit counts, and later commits in the same editor session don't. This blocks "one burst of repeated typos".
- **Tiers:**

  | Tier | Promotion rule | Suggest | Protected from autocorrect | Autocorrect target |
  |---|---|---|---|---|
  | Observed | 1 session | ✗ | ✗ | ✗ |
  | **Provisional** | ≥2 sessions on ≥2 distinct days | ✓ (ranked below dictionary words of equal prefix fit) | ✓ (typing it exactly never gets it corrected away) | ✗ |
  | **Learned (auto)** | ≥5 uses across ≥3 sessions on ≥3 distinct days, with no veto or removal | ✓ (normal learned rank) | ✓ | ✓ only for one-edit repairs with the existing margins |

- **Decay:**
  - Observed entries expire after 14 days with no new session.
  - Provisional entries expire after 45 days unused.
  - Learned (auto) entries drop back to Provisional after 180 days unused, then follow Provisional decay.
  - Explicitly taught words **never** decay.
- **Caps:** 3,000 observed + provisional + auto entries. Evict the lowest `sessions`, then the oldest `lastDay`.
- **Feedback:** when a word reaches Provisional, the strip shows the existing `LEARNED_FEEDBACK` role (`📖✓`) for that word once, with no dialog.
- **The third-use review dialog** (`UNKNOWN_REVIEW_THRESHOLD = 3`, `SharedDecoder.java:1736`) is shown **only when this switch is OFF**. With it ON, the dialog is replaced by tiering.
- **Safety invariants** (unit-tested):
  - A word that was autocorrected away and not undone does not count; a word whose correction was undone does count.
  - A word typed and then corrected by backspace-editing does not count.
  - Correction sources are never promoted into the vocabulary without C-F1 veto evidence.
- **Turning OFF:** learning stops immediately. A one-time dialog asks **Keep learned words** or **Remove auto-learned words** (taught words are always kept).
- **Storage:** new `typing_model_passive_words`. Bump `VOCABULARY_POLICY_VERSION` 1 → 2, with a migration that changes nothing for existing taught words and corrections.

### C-F3 Tap your typed word to keep it, then tap again to save it (behavior 3)

- While the typed word isn't recognized, the entered-literal slot (page 0, slot 2) shows the literal **in quotes** (`"teh"`, role `KEEP_TYPED`) instead of `📖+`.
- **First tap:** commit the literal exactly as typed plus a space, with no autocorrect for this instance. It counts as an observation for C-F2 (even if C-F2 is off, this is explicit and harmless: the evidence is simply unused). The strip then shows `Tap again to save "teh"` (role `SAVE_TYPED`) in the center slot until the next key is pressed.
- **Second tap on `SAVE_TYPED`:** teach the word immediately. It's a positive explicit choice, so no dialog is needed (the existing contract requires a positive choice, and this is one). Then show `📖✓`.
- Recognized or learned literals keep the existing behavior (the `📖−` unlearn action on swipe-up).
- **OFF state:** today's `📖+` / `📖−` actions and review dialog.

### C-F4 Hold a suggestion to remove it (behavior 4)

- Uses the A0 long-press seam, triggered after ≥450 ms with movement below the tap slop.
- The strip changes, **without a dialog**, to: `Remove "word"?` · **Remove** · **Cancel** (roles `REMOVE_CONFIRM`/`WORD`). This stays until you tap or 4 s pass.
- **Remove:**
  - If the word is taught, auto-learned, or provisional: unlearn it, removing its word pairs and correction evidence as the existing `unlearn_word` does.
  - If it's a dictionary word: add it to **blocked suggestions** (`typing_model_blocked_suggestions`, ≤512). Blocked words are never suggested and never used as autocorrect targets. They're still accepted if you type them exactly, and never "corrected".
  - Either way, the strip shows `UNLEARNED_FEEDBACK` for that word.
- Blocked suggestions appear in Learned Words → **Blocked suggestions**, where each can be deleted.
- **OFF state:** long-press does nothing (swipe gestures remain).

### C-F5 Personal next-word prediction (behavior 5)

- **Learn a pair `(w1, w2)`** when both words are committed consecutively in the same sentence, in a safe prose editor with personalization allowed, and **both** words are recognized, taught, auto-learned, or kept literals (never correction sources). Optionally learn a trigram `(w0, w1, w2)` under the same rule.
- There's at most one count per pair per session. Pairs are never learned across `. ! ?` or a newline.
- **Store:** reuse `typing_model_bigrams` (currently only holds explicit-derived pairs after the v1 migration), plus new `typing_model_trigrams`. Caps: 5,000 bigrams and 2,000 trigrams. Decay: entries unused for 90 days are removed.
- **Where it's used:**
  - The existing `Decoder.decode_next_words` (`Decoder.java:498`) already merges `personalization.suggest_next_words_with_counts` with the language pack. Personal pairs with ≥2 sessions rank above language-pack predictions.
  - Pairs also act as bounded context evidence in current-word ranking (existing "bounded bigram/trigram" contract).
- **OFF state:** no pair learning (today). Existing pairs are still used unless the user clears them. The Learned Words **Word pairs** tab shows them with delete.

### C-F6 Contact names (behavior 6)

- Turning the switch ON launches `READ_CONTACTS` runtime permission from `SettingsActivity` (an IME can't request permissions). If the permission is denied, the switch reverts to OFF with the note "Contacts permission is needed".
- Add `<uses-permission android:name="android.permission.READ_CONTACTS"/>` to the manifest. Do **not** request it anywhere else.
- **`ContactNamesIndex`** (on the worker):
  - Queries `ContactsContract.Contacts.DISPLAY_NAME_PRIMARY` only.
  - Splits names into tokens of ≥2 letters.
  - Keeps up to 5,000 tokens in memory, with proper-case surface.
  - Refreshes on a `ContentObserver`, debounced by 10 s.
  - Is dropped when the permission is revoked, the switch is turned OFF, or the IME shuts down.
- **Effect:**
  - Tokens count as recognized proper names: they are protected literals and are never autocorrected away.
  - A token is suggested with its capitalization once the typed prefix is ≥2 characters.
  - Tokens never become autocorrect *targets* from different words unless the decoder already has a one-edit repair with the existing margins.
- Nothing is persisted or backed up, and nothing is logged.
- **OFF state:** no permission request and no index.

### C-F7 Learned Words: one place for everything

The A0 tab host gets these tabs:
- **Words** (taught + auto-learned + provisional, each with a tier badge: "Taught", "Learned", "New");
- **Corrections** (existing);
- **Word pairs**;
- **Blocked corrections**;
- **Blocked suggestions**;
- plus **Emails** (B) and **Shortcuts** (D) from other lanes.

Every tab has search, delete one (with confirmation), and a clear-tab action (with confirmation). "Clear typing data" clears all tabs except Shortcuts, which are user-authored and kept, with the confirmation text saying so.

## 5. Decoder and ranking changes (owned by this lane)

- Add a `LearningPolicy` value object, sent with `Decoder.DecoderConfig` and bumping `configEpoch` when it changes, containing the six switch values.
- **Ranking inputs added:**
  - provisional tier (suggest-only, rank penalty 0.85× of learned score);
  - auto-learned tier (same as taught for suggestion; autocorrect target only with margins);
  - vetoes (block auto-apply);
  - blocked suggestions (filter);
  - contact tokens (recognized proper names);
  - personal bigram/trigram boost for next-word and context.
- All constants go in one place (`LearningPolicy` statics). Every tie is explicit (existing contract).

## 6. Implementation stages

| Stage | Content | Parallel? | Exit |
|---|---|---|---|
| **C0** | Settings sub-section, six switches with copy, `LearningPolicy` plumbing into `DecoderConfig`, policy version 2 migration skeleton, Learned Words tab registration stubs | Settings/Config parts can start before A0; tab stubs need A0 | Switches persist; "all OFF" regression suite green |
| **C1** | Backspace undo (C-F1) + vetoes (C-F1a) via `BackspaceHook` | After A0. **Blocks D1b and D3.** | `BackspaceUndoTest` (§7) green; replay "teh→the, ⌫ → teh" green |
| **C2** | Automatic learning tiers (C-F2), dialog gating, decay, OFF-migration dialog | Parallel with C1 (different code paths; one agent doing both sequentially is also fine) | `PassiveVocabularyTest` green, including safety invariants |
| **C3** | Typed-word keep/save (C-F3) | After A1 | Tests green |
| **C4** | Long-press remove + blocked suggestions (C-F4) | After A1 | Tests green |
| **C5** | Personal next-word pairs (C-F5) | After C0; parallel with C1–C4 | Tests green |
| **C6** | Contact names (C-F6): permission flow, index, ranking hook | After C0; **can be a separate sub-agent** (mostly new files) | `ContactNamesIndexTest` green; permission denial path tested |
| **C7** | Learned Words tabs, tier badges, clear-all integration, DOX and `PRODUCT.md` updates | After C1–C6 | UI tests green; DOX chain consistent |

## 7. Tests (minimum)

- **All-OFF regression:** every existing learning and autocorrect contract test passes unchanged.
- **Backspace undo:**
  - `teh␣` → `the␣`, then ⌫ gives `teh`, with the strip showing `teh`, `the`, …;
  - a second ⌫ gives `te`;
  - cursor movement closes the window;
  - after a 5 s timeout, ⌫ deletes normally;
  - a suggestion-tap replacement is undoable;
  - a late-spellcheck repair is not;
  - after 1 context veto the same context doesn't auto-correct, while other contexts still do;
  - after 2 vetoes it isn't auto-applied anywhere, but is still suggested.
- **Tiers:**
  - 3 uses in one session → stays Observed;
  - 2 sessions on 2 days → Provisional;
  - Provisional is not corrected when typed exactly, and is never an autocorrect target;
  - 5 uses / 3 sessions / 3 days → Learned;
  - decay at 14, 45, and 180 days (inject a clock);
  - an edited word doesn't count; a corrected-away word doesn't count; an undone correction counts.
- **Typed-word tap:** a first tap commits the literal; a second tap teaches; typing a new key cancels `SAVE_TYPED`.
- **Long-press:** removing a taught word unlearns it and its pairs; removing a dictionary word blocks it; a blocked word typed exactly is kept.
- **Next word:** a pair is learned only once per session, never across `.`; a personal pair outranks the pack after 2 sessions; decay after 90 days.
- **Contacts:** permission denied → switch OFF; the index is bounded; a name typed exactly is never corrected; nothing is persisted (assert no pref keys).
- **Performance:** decode p95 regression ≤10% with 3,000 passive entries, 5,000 pairs, and 5,000 contact tokens loaded.

## 8. DOX and contract updates (same change as the behavior)

- `PRODUCT.md` Design Principles, replacing "Treat vocabulary as intentional personal data: learn words only through explicit teaching …" with: "Treat vocabulary as personal data that you own. Words are learned through explicit teaching, or, when *Learn words as I type* is on, from repeated unedited use across separate sessions and days. Everything learned is visible, reversible, and decays when unused. Automatic corrections are always one backspace from undone when *Undo autocorrect with backspace* is on."
- `srcs/juloo.keyboard2/AGENTS.md`:
  - rewrite the "Vocabulary learning requires…" bullet to cover the switches and tiers;
  - rewrite "Backspace/cursor movement settles accepted corrections and never restores misspellings" → "…unless *Undo autocorrect with backspace* is on, in which case the first backspace immediately after the latest automatic change restores the source and records a veto";
  - add the Contacts permission scope.
- `suggestions/AGENTS.md`: add tiers, vetoes, blocked suggestions, personal pairs, contact index, and `LearningPolicy`; state that with all switches OFF the 2.0.119 contract holds.
- Delivery `AGENTS.md`: no change beyond the index (done by the integrator).

## 9. Risks

| Risk | Mitigation |
|---|---|
| Learned typos return | Per-session counting, multi-day requirement, edited/corrected-away exclusion, Provisional words never autocorrect-targets, decay, visible tiers, long-press remove |
| Backspace undo surprises users who expect delete | The window is only the first backspace right after the change, with a 5 s cap and a switch |
| Contacts permission scares users | Default OFF, explicit copy, names only, never stored |
| Ranking regressions | All-OFF equality suite; ranking changes limited to new inputs; throughput gate |
| Conflicts with D | D3/D1b wait for C1 (overview §5.3); C owns `PendingReplacement` |

## 10. Changelog copy

- New: press backspace right after an autocorrection to get back what you typed. FrankenKey then stops making that correction.
- New: FrankenKey can learn names and new words you use on different days, suggesting them first and correcting to them only after lots of use.
- New: tap your exact typing in the suggestion bar to keep it, and tap again to add it to your dictionary.
- New: press and hold a suggestion to stop it from being suggested.
- New: learns the word pairs you use often and predicts your next word.
- New (optional): suggest names from your contacts. Needs permission; names stay on your phone.
- Each of these can be turned off in Settings → Smart typing → Learning, with an explanation of what it does.

## 11. Done definition

C0–C7 have met their exit criteria, the all-OFF regression suite is green, DOX and `PRODUCT.md` are updated, the lane report is written, and the branch is merged by the integrator in wave order.
