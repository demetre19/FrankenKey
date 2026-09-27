# FrankenKey Typing Intelligence — Overview, Philosophy, and Lane Map

Date: 2026-09-27
Status: Scope decisions recorded from the user on 2026-09-27; each lane PRD is a build handoff. Build start, test-build upload, commits to the source branch, push, tag, and release each still follow the root `AGENTS.md` approval rules.
Baseline: FrankenKey 2.0.119 / version code 170, source `autobuild/frankenkey-autocorrect-suggestions` @ `57f1356`, delivery `master` @ `9e189d8`.

This is the entry document. Read it before any lane PRD. It owns the shared philosophy, the recorded decisions, the parallel-lane map, the integration protocol, and the shared cross-lane contracts. Each lane PRD owns its own feature scope.

| Doc | Lane | One-line scope |
|---|---|---|
| `00-Overview-and-Philosophy.md` | — | Philosophy, decisions, lane map, integration |
| `A-Suggestion-Strip-Reliability.md` | A | Every visible suggestion inserts once on the first tap; the strip stops blanking; shared extension seams |
| `B-Email-Memory-and-Domains.md` | B | Remembered email addresses; `@` domain suggestions (`gmail.com` …) |
| `C-Adaptive-Learning.md` | C | Six optional learning behaviors, each with an in-app explanation |
| `D-Power-Typing.md` | D | Text shortcuts, touch calibration surface, space-bar correction cycling, inline word completion |
| `E-Grammar.md` | E | Offline grammar rules and an explicit AI "Fix grammar" action |

---

## 1. Problem statement

User report (2026-09-27): suggestions feel janky. A suggestion often has to be tapped several times before it is inserted. Email addresses are not remembered. Typing `@` does not offer `gmail.com`. Self-learning and grammar feel weak compared with mainstream keyboards.

Root causes found in the 2.0.119 source:

1. **Dropped taps (three separate silent failures).**
   - (a) Each word change and each editor refresh submits a new decode request with a new `requestGeneration` (`suggestions/Decoder.java:2135` `RequestKey`). While the request is pending, `CandidatesView.set_decoder_state` sets every candidate view to `GONE` and nulls the request key (`suggestions/CandidatesView.java:81-85`, `:122-147`). A finger that lands in that gap lands on nothing, because `GONE` views receive no touch events.
   - (b) A tap just before the gap fails the exact-key check in `KeyEventHandler.suggestion_entered` (`KeyEventHandler.java:549`, `SharedDecoder.java:1093`) and returns silently.
   - (c) Even with a current key, `commit_correction` returns `false` silently when the tracked word doesn't match the editor (it refreshes and gives up), or when the word is `INCOMPLETE` (`KeyEventHandler.java:775-782`). The refresh then republishes the strip, so the *second* tap works. This matches the report "I have to select a word a few times".
   - Also: `onClick` reads `_items[slot]` at release time (`CandidatesView.java:363-377`). If the strip re-renders during a press, the tap can insert a **different** word from the one the finger landed on.
2. **Sloppy taps become swipes.** A 24dp vertical drift on a candidate counts as a swipe. Swiping up opens the learn/forget review instead of inserting the word (`CandidatesView.java:386-418`).
3. **No email support, and addresses can be corrupted.** Email/URI fields are "structured" and keep no state (`EditorConfig.java:183-197`), but autocorrect still runs in them: `should_try_autocorrect` checks only typing assistance (`KeyEventHandler.java:2340`), and `.` is an autocorrect separator (`:2270`). `@` and `.` are not word characters (`CurrentlyTypedWord.java:455`), so the domain part after `@` gets decoded and can be "corrected" when `.` is typed. The only existing `@` guard protects the `word.word` period conversion (`KeyEventHandler.java:1568`). Lane B confirms this with a failing test first, then fixes it.
4. **Learning is explicit-only by policy, and the typed word can't even be tapped.** The policy in `PRODUCT.md` and the source `AGENTS.md` came from the 2026-07-07 "learned typos" investigation. New words are learned only through Teach or a correction choice after editing. The third repeated unknown word opens a dialog. Backspace never restores an autocorrected word. When a typed word is unknown, `expose_learn_action` puts `📖+` into the slot where the literal would appear (`CandidatesView.java:295-308`), so there's no one-tap way to keep what you typed.
5. **Grammar depends on the phone.** Grammar uses only the platform spell-checker's sentence API, so quality varies by phone, and it is off by default.

## 2. Philosophy

These principles are binding on every lane. If a lane requirement conflicts with one, the principle wins; raise the conflict instead of shipping around it.

1. **A visible suggestion is a promise.** If it is on screen and you tap it, it goes in exactly once. If it can't, the strip says so visibly. It never fails silently.
2. **Nothing you typed is ever lost.** Every automatic change (autocorrect, shortcut expansion, suggestion replacement, grammar fix) can be undone with one backspace or one tap, and the original text comes back.
3. **Suggest freely, correct cautiously.** New and learned knowledge shows up as a suggestion long before it can become an automatic correction. Automatic changes need much stronger evidence than suggestions.
4. **Learn from what you do, forget what you don't use.** Learning is evidence-based, bounded, and decays over time. One typo in one session must never become permanent vocabulary.
5. **Every adaptive behavior can be explained and switched off.** Each optional learning behavior has a Settings switch whose summary says what it does and why you would want it. With a switch off, that behavior's learning and correction results are identical to 2.0.119. The acceptance and strip fixes from Lane A still apply, because they aren't learning behaviors.
6. **You can see and delete everything learned.** Every learned artifact (words, word pairs, email addresses, blocked corrections, blocked suggestions, shortcuts, touch calibration) is listed, searchable, deletable, and cleared by "Clear typing data".
7. **Private by default.** All learning stays on the device, in credential-protected storage. Vocabulary keeps its current backup behavior. New sensitive stores (email addresses) are excluded from Android backup and from settings export. Contact names are never stored. The network is used only when you explicitly tap an AI action, after a disclosure.
8. **Structured text gets structured help.** Email addresses, URLs, and domains get completion, never spelling correction.
9. **Fast and calm.** No decode or storage work on the IME main thread. The strip never flickers while you type. Feedback is haptic and subtle rather than modal. New dialogs are avoided.
10. **Deterministic and testable.** Thresholds, caps, decay rates, and ranking ties are explicit constants with unit tests. Every principle above has at least one automated check.

## 3. Recorded user decisions (2026-09-27)

| Item | Decision |
|---|---|
| Tap reliability (Lane A) | Proceed; needs no policy change. |
| Email memory: saved addresses, prefix recall, `@` domains, no autocorrect in email text, Settings switch, Learned Words management (Lane B) | **All approved.** |
| Backspace-undo of autocorrect + automatic (evidence-based) learning (Lane C) | **Approved.** |
| Behaviors 1–6: backspace undo, automatic word learning, tap-typed-word-to-keep/save, long-press remove, personal next-word, contact names | **Approved as optional.** Each gets a Settings switch **with an explanation of why you'd use it** (Lane C). |
| Behaviors 7–10: text shortcuts, touch calibration, space-bar correction cycling, inline completion | **Approved as standard features** (Lane D). Interpretation: they follow the existing master Suggestions/Autocorrect switches and get no separate opt-in. Confirm in Q1. |
| Behavior 11: cross-device sync | **Excluded.** |
| Offline grammar rules + AI "Fix grammar" action (Lane E) | **Both approved.** |
| Delivery format | One overview plus five handoff PRDs with implementation stages, run in parallel where possible, with explicit notes where they can't. |

### Open questions (answer before the named stage; each has a recommended default so work isn't blocked)

- **Q1 (D0):** Are items 7–10 "always on under the master switches" (assumed), or does each also get its own switch? Recommended: no extra switches, except that inline completion follows the Suggestions switch.
- **Q2 (C0):** Defaults for the optional behaviors 1–6. Recommended: 1 ON, 2 ON, 3 ON, 4 ON, 5 ON, 6 OFF (6 needs the Contacts permission).
- **Q3 (D3):** No space-bar direction is really free.
  - In the dense layout, swipe down is `switch_backward` (`res/xml/bottom_row.xml:6`, `key8`).
  - In the default clean layout (`res/xml/clean_text.xml:41`), down and right have no value, but `Pointers.getNearestKeyAtDirection` (`Pointers.java:255-277`) snaps a swipe to the nearest assigned direction within about ±67°. So today, a straight down swipe opens emoji or GIF, and a straight right swipe toggles coding mode or opens GIF.
  - Recommended: swipe down cycles corrections **only** inside the correction-undo window, and **only** for a near-vertical swipe (the exact S direction ±1 of 16). It's intercepted in `Pointers` before the nearest-direction snap. Outside the window, nothing changes.
  - The space-bar "accept completion" gesture is **dropped** (D4 accepts by tap only) for the same reason.
- **Q4 (B2):** Remember email addresses typed in normal prose fields (not only in email fields)? Recommended: yes, as a lower-weight source.
- **Q5 (E3):** Which model does AI "Fix grammar" use? Recommended: a separate "Writing model" setting that defaults to the selected Reader AI model.

## 4. Policy and contract changes this program makes

These documents say, deliberately, "never" to things these lanes now do. Each lane updates its lines in the same change as the code (DOX contract), and **each change applies only while the relevant switch is ON**:

| Document | Current line (paraphrased) | Change owned by |
|---|---|---|
| `PRODUCT.md` Design Principles | "learn words only through explicit teaching or a deliberate correction choice … never from ordinary typing" | C — rewrite as "learn from repeated, unedited use across sessions when *Learn words as I type* is on; always visible and reversible" |
| Source `srcs/juloo.keyboard2/AGENTS.md` | "Structured/terminal fields remain stateless" | B — add the email-memory exception |
| Source `srcs/juloo.keyboard2/AGENTS.md` | "Ordinary commits … never create unigram, bigram, or correction evidence" | C |
| Source `srcs/juloo.keyboard2/AGENTS.md` | "Backspace/cursor movement settles accepted corrections and never restores misspellings" | C (backspace undo) |
| Source `suggestions/AGENTS.md` | "Only exact current `RequestKey` may publish, commit, learn/forget, or accept actions" | A — publishing still needs the exact key; acceptance uses a content-validated ticket |
| Source `suggestions/AGENTS.md` | third unknown commit → review dialog | C — dialog only while automatic learning is OFF |
| Source `srcs/juloo.keyboard2/AGENTS.md` + root `AGENTS.md` Reader AI line | OpenRouter use limited to URL articles / clipboard / page capture / EPUB | E — add "current editor text, only via the explicit Fix grammar action" |
| Source `srcs/juloo.keyboard2/AGENTS.md` | Grammar = system checker, latest completed sentence | E — add offline rules |

## 5. Parallel lanes

### 5.1 Can this run in parallel?

**Mostly yes, with one short gate at the start and three dependencies later.** The five lanes touch a small set of shared files, listed below. The plan makes them parallel by (a) landing a tiny seam refactor first (Stage A0), and (b) giving each shared file a single owner lane, with other lanes reaching it only through the seams.

### 5.2 Shared-file ownership (conflict map)

| File | Owner | Also touched by | How conflicts are avoided |
|---|---|---|---|
| `suggestions/CandidatesView.java` | A | B, C, D | A0 adds a `CandidateRole` extension point and one `candidate_accepted(ticket, candidate)` dispatch. Other lanes add roles and handlers, and never edit the touch or tap logic. |
| `suggestions/SharedDecoder.java` (presentation/acceptance) | A | B, C, D | A0 adds `CandidateSource` (pinned external candidates merged at presentation time). B and D register sources; they don't edit merge logic. |
| `suggestions/Decoder.java` (ranking) | C | D4 | D4 only reads the ranked result (completion flag). C owns every scoring change. |
| `suggestions/PersonalizationStore.java` | C | — | B and D use their own stores. |
| `KeyEventHandler.java` | A (accept path), C (backspace/undo) | B, D | A0 extracts the `SuggestionAcceptor` and `BackspaceHooks` seams. C owns the undo window. D3 plugs into C's undo window (hard dependency). |
| `Pointers.java` | A (A0 seam only) | D3 | A0 adds `SpaceGestureHook`, consulted with the **raw** swipe direction before `getNearestKeyAtDirection`, so contextual gestures don't steal corner actions. |
| `Keyboard2.java` | E (grammar section) | A, B | Line-disjoint regions; trivial merges. |
| `EditorConfig.java` | B | C | B adds `should_use_email_memory`. C adds nothing (it reuses `should_use_personalization`). |
| `ReaderAiAction.java` + Reader AI files | E | — | Exclusive. |
| `LearnedWordsActivity.java` | C | B, D | A0 converts it to a tab host, with one tab class per lane (`EmailsTab` B, `ShortcutsTab` D, others C). |
| `res/xml/settings.xml`, `res/values/strings.xml` | each lane its own block | all | Each lane adds its own `PreferenceScreen` or strings block, under reserved key prefixes (§7). Merges are textual. |
| `AGENTS.md` / `PRODUCT.md` | each lane its own lines | all | The integrator merges them; lanes never rewrite another lane's line. |
| `build.gradle.kts` version | Integrator only | — | Lanes never bump the version. |

### 5.3 Dependency graph

```
A0 (seams, ~1 day) ──┬──> A1..A4 ───────────────────────────────┐
                     ├──> B3 (strip UI) ─> B4 ─> B5               │
                     ├──> C3 (typed-word tap), C4 (long-press)    │
                     └──> D2, D4 (completion UI)                   │
B1, B2 ─────────────────(no gate; pure logic + store)──> B3        │
C0, C1, C2, C5, C6 ─────(no gate; store/ranking/undo)──> C3, C4, C7│
C1 (undo window) ────────────────────────────────────> D3 (space cycling)
C0 (store kinds) ────────────────────────────────────> D1b (shortcut expansion on space)
E1..E5 ─────────────────(fully independent)──────────────────────────┘
                                                        └──> Integration waves
```

Hard dependencies (these **cannot** run in parallel):

1. **A0 → every stage that renders or accepts a new candidate role** (B3, C3, C4, D2, D4).
2. **C1 → D3.** Space-bar cycling reuses C1's correction-undo window (`PendingReplacement`). Two lanes changing it at the same time would conflict in logic, not only in text.
3. **C1 → D1b.** Expanding a shortcut automatically on space must be undoable with backspace (principle 2), and that uses C1.
4. **A1 → C3/C4.** Typed-word tap and long-press ride on the new ticket acceptance.

Everything else can run in parallel.

### 5.4 Recommended waves (5 agents max)

| Wave | Parallel lanes/stages | Gate to exit |
|---|---|---|
| **0** | A0 (one agent). In parallel, pure-logic stages that touch no shared file: B1, C0 (settings plumbing only), E1 | A0 merged to the integration branch; full unit suite green |
| **1** | A1–A3 · B2 · C1, C2 · D1a (shortcut store/UI), D0 · E2–E3 | Each lane's focused tests green; integration merge green |
| **2** | A4 · B3–B4 · C3, C4, C5, C6 · D1b, D3 (after C1) · E4 | Integration build; emulator proof; signed canonical test APK uploaded to phone (Test Build 1) |
| **3** | B5 · C7 · D2, D4 · E5 · cross-lane hardening | Integration build; replay suite; Test Build 2 → user testing → release |

Estimated effort in agent-days (single focused agent): A 3–4, B 4–5, C 8–10, D 5–6, E 5–6. With parallel lanes the critical path is roughly A0 → C1 → D3 → hardening, about 8–10 days, compared with about 27 serially.

### 5.5 Which lanes can start right now with zero coordination

- **E (grammar)**, all stages.
- **B1–B2** (email parser, validator, store, learning events).
- **C0–C2** (settings plumbing, veto store and backspace undo, automatic learning tiers). These are KeyEventHandler-heavy, so A0 should land first if possible. If A0 isn't merged yet, C1 must keep its edits inside `commit_correction`/backspace code and stay out of `suggestion_entered`.
- **D1a** (shortcut store and management UI, without expansion).

## 6. Integration protocol

- **Branches.** Each lane branches from `autobuild/frankenkey-autocorrect-suggestions` @ `57f1356` as `ti/lane-a` … `ti/lane-e`. The integrator keeps `ti/integration`. Each lane uses its own worktree (`git worktree add ../FrankenKey-ti-a ti/lane-a`) so builds don't collide.
- **Merge order in each wave:** A → C → B → D → E, which is the order of seam dependency. After each merge: `testDebugUnitTest` (full), then `RealImeThroughputInstrumentedTest` focused at zero interval.
- **Rebase policy.** Lanes merge `ti/integration` into their branch at every wave boundary, using merges rather than rebases on shared branches.
- **Versioning and builds.** Only the integrator bumps `versionCode`/`versionName`, builds the signed `assembleRelease`, runs `verifyReleaseIdentity`, replaces the canonical APK, and uploads it to the phone (root `AGENTS.md` Release Contracts). Lane agents verify with unit tests and emulator debug builds, which never go into repository or updater paths.
- **DOX.** Each lane updates the `AGENTS.md` lines listed in §4 and in its own PRD, in the same commit as the behavior. The integrator checks that the chain is consistent at each wave exit.
- **Changelog.** Each lane writes user-visible bullets in its PRD's "Changelog copy" section. The integrator assembles `CHANGELOG.md` and `fastlane/.../changelogs/<vc>.txt`. No other brand names appear in changelog or UI text (`PRODUCT.md` anti-references).
- **Release shape.** Recommended: one release after Wave 3 (2.0.120+), or two releases (after Wave 2: A + B + E + C1/C2; after Wave 3: the rest) if the user wants the tap fix sooner.

## 7. Shared cross-lane contracts

### 7.1 Settings keys (reserved prefixes)

| Prefix | Lane | Example |
|---|---|---|
| `ti_strip_` | A | `ti_strip_haptic_accept` |
| `ti_email_` | B | `ti_email_memory_enabled` |
| `ti_learn_` | C | `ti_learn_backspace_undo`, `ti_learn_auto_words`, `ti_learn_typed_word_tap`, `ti_learn_long_press_remove`, `ti_learn_next_word`, `ti_learn_contacts` |
| `ti_power_` | D | `ti_power_space_cycle` (only if Q1 adds switches) |
| `ti_grammar_` | E | `ti_grammar_offline_rules`, `ti_grammar_rule_<id>`, `ti_ai_writing_model` |

All new settings sit under the existing **Typing assistance** category in a sub-screen called **Smart typing**. The sub-screen has one section per lane: *Suggestions*, *Email*, *Learning*, *Shortcuts & gestures*, *Grammar*. This avoids growing the top-level list.

### 7.2 Storage locations

| Data | Store | Backup |
|---|---|---|
| Existing vocabulary, corrections, touch calibration | `PersonalizationStore` (default prefs, `typing_model_*`) | As today |
| New C data (automatic word tier, vetoes, blocked suggestions, personal word pairs) | `PersonalizationStore`, new `typing_model_*` keys, policy version 2 | As today |
| Email addresses (B) | New named prefs `email_memory.xml` (credential-protected, not direct-boot) | **Excluded** from `backup_rules.xml`, `data_extraction_rules.xml`, and `SettingsBackup` export |
| Text shortcuts (D) | New named prefs `text_shortcuts.xml` | **Included** in `SettingsBackup` (user-authored, like snippets) |
| Contact names (C6) | Memory only, on the worker | Never stored |
| Grammar rule toggles (E) | Default prefs | As today |

"Clear typing data" clears everything in rows 1–3 and 5, after one confirmation that names each category.

### 7.3 Candidate roles

A0 defines `CandidateRole`: `WORD`, `ENTERED_TEXT`, `NEXT_WORD`, `EMOJI`, `LEARN_ACTION`, `UNLEARN_ACTION`, `LEARNED_FEEDBACK`, `UNLEARNED_FEEDBACK` (existing) plus `EMAIL_ADDRESS`, `EMAIL_DOMAIN` (B), `KEEP_TYPED`, `SAVE_TYPED`, `REMOVE_CONFIRM` (C), `SHORTCUT`, `COMPLETION` (D). No debug/source labels are shown (existing contract); the only visual distinction is the dimmed completion suffix (D4).

### 7.4 Editor eligibility (single table all lanes use)

| Editor | Word suggestions | Autocorrect | Learning (C) | Email (B) | Shortcuts (D) | Offline grammar (E) | AI Fix grammar (E) |
|---|---|---|---|---|---|---|---|
| Prose (normal, message, subject, web edit) | ✓ | ✓ | ✓ unless no-personalized-learning flag | recall after `@`/prefix ≥3; learn on completion | ✓ | ✓ | ✓ |
| Email address field | — (replaced by email candidates) | ✗ | ✗ | ✓ full | ✗ | ✗ | ✗ |
| URI / search | ✓ | as today | ✗ | `@` only | ✗ | ✗ | ✗ |
| Password / numeric / phone / unknown | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |
| Termux `TYPE_NULL` / terminal | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |
| Any field with `IME_FLAG_NO_PERSONALIZED_LEARNING` | ✓ | ✓ | ✗ (explicit Teach still allowed, as today) | recall ✗, learn ✗ | ✓ | ✓ | ✓ (never cached) |

## 8. Program-level verification

- **Replay suite (owned by A, extended by each lane).** Scripted IME sessions on the emulator. Each script replays key taps, candidate taps (including taps while a request is pending), backspaces, and gestures, and asserts the exact final editor text. It runs at the integration gate of every wave.
- **Regression proof for "switch OFF = 2.0.119".** Each C behavior switched off must pass the current `KeyEventHandlerLearningContractTest`, `KeyEventHandlerAutocorrectContractTest`, and `SuggestionPersonalizationTest` unchanged.
- **Phone acceptance (user).** Gmail compose To field; Chrome sign-in email field; the Amazon app sign-in; Samsung Messages prose; Termux (must be untouched).
- **Performance.** The p95 time from keystroke to the strip being updated must not regress by more than 10% against 2.0.119 in `RealImeThroughputInstrumentedTest`.

## 9. Out of scope (whole program)

- Cloud sync or federated learning of any kind.
- Learning from other apps' content: notifications, message history, or accessibility-captured pages.
- Swipe/glide typing.
- Multilingual grammar beyond English (`en_AU`, `en_GB`, `en_US`).
- In-editor ghost text. FrankenKey commits text directly; styling composing spans behaves differently from app to app. Completion is shown in the strip instead (D4).

## 10. Adversarial review log (2026-09-27)

A second pass re-checked every claim against the 2.0.119 source and attacked each design. Results, all already applied to the lane PRDs:

| # | Finding | Evidence | Change |
|---|---|---|---|
| R1 | A third silent tap failure: `commit_correction` returns `false` with no feedback on a tracked-word mismatch or `INCOMPLETE`, then refreshes. This is the most likely cause of "tap it a few times" in slow editors | `KeyEventHandler.java:764-797` | A-F2b: synchronous refresh + one retry; otherwise the visible reject cue; no bare returns |
| R2 | A tap can insert a *different* word if the strip re-renders mid-press | `onClick` reads `_items[slot]` at release (`CandidatesView.java:363-377`) | A-F2: ticket captured at `ACTION_DOWN` |
| R3 | The first design (reject if the word changed at all) would have shaken on most fast-typing taps, because stale-but-visible items were computed for a shorter prefix | Design flaw | A-F2 "intent wins": replace the current word within the same slot; reject only on a slot change. A-F1: old-word candidates removed when the slot changes |
| R4 | Autocorrect can already run inside email fields and on `.` inside addresses in prose | `KeyEventHandler.java:2270`, `:2340`; guard only at `:1568` | B-F7 moved to B1 (immediate), with a failing test first |
| R5 | `@name` is a mention in chat apps; domain suggestions would be noise | Design flaw | Email context requires a non-empty local part |
| R6 | A space after a domain completion breaks `.com.au` or subdomains | Design flaw | Never add a trailing space |
| R7 | Hint text containing "email" (for example "Email subject") would turn prose fields into email fields | Design flaw | Strict hint regex, single-line only, "soft" email fields that keep word suggestions |
| R8 | Credential-protected email store is unreadable before first unlock | Android direct boot | Fail closed and retry after unlock |
| R9 | No space-bar direction is really free: unassigned directions snap to the nearest corner within about ±67°, so a clean-layout down swipe opens emoji/GIF | `Pointers.java:255-277`, `clean_text.xml:41` | The `SpaceGestureHook` moved to `Pointers` (raw direction, before the snap); D3 limited to near-vertical swipes inside the undo window; D4 space-swipe accept dropped (tap only) |
| R10 | Autocorrect is asynchronous, so a backspace-undo window keyed to the separator would misfire | `stage_pending_autocorrect_boundary` → `stage_pending_replacement` (`KeyEventHandler.java:1253-1345`) | C-F1: the window opens when the correction lands; backspace before it lands keeps today's freeze; held-backspace repeats never undo |
| R11 | Automatic learning would re-learn *consistent* typos (`teh` typed on many days), the exact 2026-07 failure | Design flaw | Near-dictionary guard: passive use can't promote a word within one edit of a common word without an explicit signal |
| R12 | "Two distinct days" before a new name is suggested is too slow; raw editor sessions are too weak, because many apps restart input per message | Design flaw | Provisional after 3 uses in 2 sessions ≥60 min apart; becoming a correction target still needs 3 days |
| R13 | Several grammar patterns misfire on valid English ("its time complexity", "better then we'll go"); double-space collapsing conflicts with double-space-period | Precision review | `ITS_ITS` and `THEN_THAN` narrowed; `SPACE_DOUBLE` removed |
| R14 | Sentence-end-only grammar never runs on chat messages without punctuation; an IME-action trigger fires after the text has already been sent | Design flaw | Offline rules run on a word-boundary sliding window; IME-action trigger removed |
| R15 | Replacing a whole field with plain AI text flattens rich spans (mention chips, links, bold); many editors return partial `ExtractedText` | Android IME behavior | Minimal back-to-front segment edits with rollback; require a selection when the whole field isn't provably returned |
| R16 | "Switch off = byte-for-byte 2.0.119" conflicted with the unconditional Lane A fixes | Wording | Principle 5 scoped to learning/correction results |

Confirmed unchanged by the review: the strip's `GONE`-on-`PENDING` blanking (A-F1); the 24dp swipe threshold (A-F4); structured-field no-learning policy (B needs an explicit exception); `SettingsBackup` exporting all default prefs (B's separate store is required); the unused `undoToken`/`learnSourceOnUndo` plumbing (C1 reuses it); next-word decoding already existing (`Decoder.decode_next_words`, C5 extends it); replacement-rule targets being single words only (D needs its own shortcut store).
