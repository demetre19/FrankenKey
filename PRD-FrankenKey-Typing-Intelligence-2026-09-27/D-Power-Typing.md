# Lane D Handoff PRD — Power Typing: Shortcuts, Touch Calibration, Correction Cycling, Inline Completion

Date: 2026-09-27
Lane: D
Status: Behaviors 7–10 approved as standard features (2026-09-27). Q1 and Q3 (overview §3) have defaults.
Depends on:
- A0 for D2 and D4;
- **C1 for D1b and D3** (these cannot run before C1 lands);
- D1a and D0 have no dependency.

Parallel with: A, B, E fully; C except as noted above.
Read first: `00-Overview-and-Philosophy.md` (principles 1–3, 9; §5.3).

## 0. Handoff essentials

| Item | Value |
|---|---|
| Source checkout (Mac mini) | `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect` |
| Base / branch / worktree | `57f1356` / `ti/lane-d` / `../FrankenKey-ti-d` |
| DOX chain | source `AGENTS.md` → `srcs/AGENTS.md` → `srcs/juloo.keyboard2/AGENTS.md` → `suggestions/AGENTS.md`; `res/AGENTS.md`; `srcs/layouts/AGENTS.md` (not edited; see D3) |
| New files | `suggestions/shortcuts/ShortcutStore.java`, `ShortcutCandidateSource.java`, `ShortcutsTab.java`, `ShortcutEditDialog.java`; `suggestions/CompletionPolicy.java` |
| Touched files | `Pointers.java` (only via the A0 `SpaceGestureHook`), `KeyEventHandler.java` (only via C1's undo window API), `suggestions/CandidatesView.java` (only the `COMPLETION` rendering hook added by A0), `suggestions/PersonalizationStore.java` (read-only touch calibration stats accessor), `SettingsBackup.java` (export shortcuts), `res/xml/settings.xml`, strings |
| Tests | new `ShortcutStoreTest`, `ShortcutExpansionTest`, `CorrectionCycleTest`, `CompletionPolicyTest`; extend `SettingsBackupTest`, `SpacebarGestureLayoutTest`; replay scripts |

## 1. Objective

Four behaviors that make fast typists faster:
- (7) personal text shortcuts that expand as you type;
- (8) the existing touch-zone learning, made visible and resettable;
- (9) swipe down on the space bar right after a correction to cycle to the next suggestion;
- (10) word completion shown in the strip that you accept with one tap.

These are standard behaviors (no separate opt-in, Q1). They follow the existing master **Suggestions** and **Autocorrect** switches.

## 2. Current behavior (2.0.119)

- **Exact replacement rules** exist (`PersonalizationStore.ReplacementRule`, ≤512, `MAX_REPLACEMENT_RULES`). The target must be a single learnable word (`PersonalizationStore.java:418-422`), so they can't hold phrases. They are created from the unknown-word review dialog and edited in Learned Words → Corrections.
- **Snippets** are a separate strip of saved phrases in fixed slots. They don't expand from a typed trigger.
- **Touch calibration** exists: it's worker-confined, active after 20 samples, capped at 4,096 samples, with offset ≤0.2 key widths (`PersonalizationStore.java:1696-1698`). It's invisible in Settings and only cleared with all typing data.
- **Space-bar swipes** (`res/xml/bottom_row.xml:6`):

  | Swipe | Action |
  |---|---|
  | NW | clipboard |
  | NE | clean-mode toggle |
  | SW | emoji |
  | SE | GIF |
  | up (`key7`) | `switch_forward` |
  | down (`key8`) | `switch_backward` |
  | left (`key5`) and right (`key6`) | no value |

  That's the dense `bottom_row.xml`. The default **clean** layout (`clean_text.xml:41`) defines only the four corners.

  **Important:** a direction with no value is not free. `Pointers.getNearestKeyAtDirection` (`Pointers.java:255-277`) scans ±3 of 16 directions (about ±67°) and snaps to the nearest assigned one. So in the clean layout a straight down swipe triggers emoji or GIF today, and a straight right swipe triggers coding mode or GIF.

- There's no completion affordance; a completion shows up only as an ordinary candidate.

## 3. Functional requirements

### D-F1 Text shortcuts (behavior 7)

**D1a: store and management** (no dependency)
- `ShortcutStore`: named prefs `text_shortcuts`, one versioned JSON value.
- Entry: `{trigger, expansion, createdDay, lastUsedDay, uses}`.
  - Trigger: 1–16 characters of letters, digits, or `'`, stored NFC lowercase, unique, and **not** a dictionary word (warn on save, but allow with confirmation).
  - Expansion: 1–500 UTF-16 characters. Any characters, including spaces, punctuation, emoji, and newlines. A newline is inserted literally, not as the Send action.
- Maximum 300 shortcuts.
- Management UI:
  - Settings → Smart typing → Shortcuts & gestures → **Text shortcuts**. This opens the `ShortcutsTab` in Learned Words (A0 host) or a standalone screen before A0 lands.
  - The screen supports add (+), edit, delete (with confirmation), and search.
  - Starter examples are shown as placeholder hints only, never pre-inserted: `omw` → "On my way!", `ty` → "Thank you!".
- **Backup:** included in `SettingsBackup` export and import (user-authored, like snippets). The Android-backup exclusion **does not** apply. Import merges by trigger, and on a clash the imported entry wins, after a confirmation that lists the clashes.

**D1b: expansion** (after **C1**)
- `ShortcutCandidateSource` (A0 `CandidateSource`): when the current word exactly equals a trigger (case-insensitive), pin the expansion as the **first** candidate (role `SHORTCUT`). The label is the expansion, truncated to 24 characters with `…`.
- **Tap:** replace the trigger with the expansion. If the expansion ends in a newline, add no trailing space.
- **Automatic expansion on separator:**
  - Happens only when **Autocorrect** is ON and the editor allows autocorrect (overview §7.4).
  - Space or punctuation after an exact trigger expands it.
  - The expansion opens C1's undo window, so an immediate backspace restores the trigger. If C's backspace-undo switch is OFF, the undo window for shortcuts **still applies** (principle 2), because a shortcut is a user-authored rule and silent irreversible expansion is not acceptable.
  - C1 must expose `UndoWindow.open(source, target, separator, kind=SHORTCUT)` independently of the `ti_learn_backspace_undo` switch; that switch gates only autocorrect undo.
- Shortcuts never expand in email, URI, password, numeric, or terminal editors, nor inside an email token.
- Shortcut use never creates C learning evidence.

### D-F2 Touch calibration surface (behavior 8)

- Keep the existing engine unchanged, including the bounds and the complete-evidence-only rule.
- Add a read-only `PersonalizationStore.touch_calibration_stats()` returning `{samples, active}`. It's called on the worker, and the result is posted to Settings.
- Settings → Smart typing → Shortcuts & gestures → **Adaptive touch zones**, a status row:
  - "Learning your touch: 12 of 20 taps", or "Active: adjusted from 1,284 taps".
  - Action: **Reset touch zones**, with confirmation. It clears only the touch keys (`typing_model_touch_*`).
- **Training exclusions** (defense in depth; the existing contract already excludes corrected-source touches): no samples from email tokens (B), shortcut triggers (D1b), or undone corrections (C1).

### D-F3 Space-bar correction cycling (behavior 9; after C1)

Default per Q3: **contextual swipe-down.**
- While C1's undo window is open (right after an autocorrect, suggestion replacement, or shortcut expansion), a **swipe down on the space bar** replaces the current target with the **next** alternative from the same decoder result (the ranked words retained with the ticket). Order: `target → alt2 → alt3 → … → source (the literal) → target`, up to 6 entries. Each step:
  - verifies that the text before the cursor equals `currentAlt + separator`;
  - replaces it in one batch edit;
  - keeps the undo window open, with an updated `target`;
  - highlights the active alternative in the strip (pressed state).
- Outside the undo window, every space-bar swipe resolves exactly as today (`switch_backward` in dense; the nearest corner in clean).
- Implementation: the A0 `SpaceGestureHook` in `Pointers`, called with the **raw** 16-way direction before the nearest-direction snap.
  - It returns `handled` only when the undo window is open **and** the raw direction is straight down (S ±1 of 16, about ±22°).
  - Diagonal swipes still reach the corner actions even inside the window.
  - The layout XML is **not** edited.
- Cycling to the literal records a C1 veto, exactly as backspace-undo does.
- If Q3 is answered with a different direction, only the raw-direction constant changes.

### D-F4 Inline completion in the strip (behavior 10)

- **`CompletionPolicy`** marks the top-ranked candidate as a completion when all of these hold:
  - the typed prefix is ≥2 code points;
  - the candidate starts with the prefix (case-insensitive) and is ≥2 code points longer;
  - the candidate isn't the literal;
  - the candidate's score beats the runner-up by the explicit margin `COMPLETION_MARGIN_Q8` (start at 1.6×, and tune against the replay corpus);
  - the prefix is not itself a recognized complete word with higher frequency.
- **Rendering** (A0 `COMPLETION` role): the middle slot shows the prefix at normal weight and the remainder at 55% alpha, for example **prob**<span>ably</span>. The accessibility description reads "Complete to probably".
- **Accept:**
  - **tap it** (normal acceptance, exactly once).
  - A space-bar swipe-right accept was considered and **dropped**. Because of the nearest-direction snap, a swipe right in the clean layout currently reaches coding mode or GIF, so a contextual accept gesture would make those corner actions unpredictable whenever a completion is visible. Revisit only with explicit user approval and a Pointers-level design like D3's.
- Completion never auto-applies on a plain space; plain space follows normal autocorrect rules. It follows the master **Suggestions** switch.
- There is no in-editor ghost text (overview §9).

## 4. Implementation stages

| Stage | Content | Parallel? | Exit |
|---|---|---|---|
| **D0** | Settings section "Shortcuts & gestures"; resolve Q1/Q3 defaults in code constants | Immediately | Settings persist |
| **D1a** | `ShortcutStore`, management UI (standalone screen until A0's tab host lands), backup export/import | **Immediately** | `ShortcutStoreTest`, `SettingsBackupTest` green |
| **D2** | Touch calibration stats, status row, reset, training exclusions | After A0 (Settings sub-screen) | Tests green |
| **D1b** | `ShortcutCandidateSource`, tap acceptance, separator expansion through C1's undo window | **After C1 merged** | `ShortcutExpansionTest` + replay "omw␣ → On my way! ␣, ⌫ → omw" green |
| **D3** | Space-bar correction cycling | **After C1 merged** | `CorrectionCycleTest` + replay green; `switch_backward` still works outside the window |
| **D4** | `CompletionPolicy`, `COMPLETION` rendering, tap-to-accept | After A0 (and A2 for stable rendering) | `CompletionPolicyTest` green; replay precision ≥95% on the corpus (completion shown ⇒ user wanted it) |

Parallelism inside the lane: D1a, D0, D2, and D4 can go to one agent while it waits for C1. D1b and D3 are serialized behind C1 and then run back to back.

## 5. Tests (minimum)

- **Store:** trigger validation, a dictionary-word warning path, 301st insert rejected, JSON round-trip, corrupt JSON handled safely, export/import merge with clash confirmation.
- **Expansion:**
  - tap expansion;
  - separator expansion only with Autocorrect ON;
  - no expansion in email, URI, password, or terminal editors, or inside an email token;
  - ⌫ restores the trigger even with C's backspace-undo OFF;
  - a newline expansion adds no trailing space.
- **Calibration:** stats before and after 20 samples; reset clears only touch keys; email and shortcut touches aren't sampled.
- **Cycling:**
  - order through the alternatives and back to target;
  - cycling to the literal records a veto;
  - outside the window, `switch_backward` fires in dense and the emoji/GIF corner snap is unchanged in clean;
  - inside the window, a diagonal (SW/SE) swipe still opens emoji/GIF;
  - any other key closes the window.
- **Completion:**
  - margin gate;
  - a prefix that is a frequent complete word → no completion;
  - a tap accepts with a space;
  - space-bar swipes are unchanged whether or not a completion is shown (regression);
  - it never auto-applies on space.

## 6. DOX and contract updates

- `srcs/juloo.keyboard2/AGENTS.md`:
  - Add a bullet: "Text shortcuts are user-authored trigger→expansion rules (≤300) stored in `text_shortcuts`, exported with settings backups. They expand only in prose editors (tap, or separator with Autocorrect on) and always open a one-backspace undo window. Space-bar swipe down cycles alternatives only inside that window; otherwise it keeps its layout action. Strip completions are accepted by tap only."
  - Extend the space-bar sentence of the layout contract, if one exists.
- `suggestions/AGENTS.md`: add the `COMPLETION` role rule, `CompletionPolicy` constants, and the `ShortcutCandidateSource` precedence (a shortcut beats all ranked words; an exact replacement rule beats a shortcut when both match the same trigger).
- `suggestions/AGENTS.md` touch calibration line: add the Settings status/reset and the email/shortcut/undo training exclusions.

## 7. Risks

| Risk | Mitigation |
|---|---|
| A shortcut trigger collides with a real word (`ty`, `ur`) | Warning on save; expansion only on an exact trigger; one-backspace undo; tap-to-keep the literal (C3) |
| Swipe-down conflict with `switch_backward` (dense) and with the emoji/GIF corner snap (clean) | Only inside the 5 s undo window, only for a near-vertical raw direction, intercepted before the snap; diagonals unchanged; Q3 alternative ready |
| Completion shown too often (noise) | Margin gate tuned on the replay corpus; ≥95% precision target |
| Waiting on C1 stalls the lane | D1a, D0, D2, and D4 fill the wait (≈60% of the lane) |

## 8. Changelog copy

- New: text shortcuts. Type a short trigger like "omw" and it expands to your full phrase. Backspace right away to undo. Manage them in Settings → Smart typing → Shortcuts & gestures.
- New: after a correction, swipe down on the space bar to cycle through other suggestions.
- New: when FrankenKey is confident how a word ends, the rest is shown faded in the suggestion bar. Tap it to accept.
- Settings now show how much your touch zones have adapted, with a reset option.

## 9. Done definition

D0–D4 have met their exit criteria, DOX is updated, the lane report is written, and the branch is merged in wave order (D1b and D3 after C1).
