# PRD: FrankenKey Typing Intelligence — Section C: Adaptive Learning (Six Optional Behaviors)

Date: 2026-09-27 (launch 2026-09-28)
Status: **Approved** — backspace undo + automatic learning (overview step 3), and behaviors 1–6 as optional switches with explanations (2026-09-27); Q2 defaults adopted (1–5 ON, 6 OFF). Launch authorized 2026-09-28.
Source repo: `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect`, base `autobuild/frankenkey-autocorrect-suggestions` @ `57f1356`.

## 1. Goal

Mainstream-level self-learning without the 2026-07 "learned typos" failure: learn from repeated, unedited, multi-session use; every automatic change is one-backspace undoable; everything learned is visible and deletable; each behavior has its own explained switch.

## 2. Canonical specification

`plan/PRD-FrankenKey-Typing-Intelligence-2026-09-27/C-Adaptive-Learning.md` is binding (C-F1 through C-F7, tiers/decay/caps, C-F1a vetoes, C-F2 near-dictionary guard, stages C0–C7, tests, DOX lines, changelog). Overview principles 2–6, §4, §7 bind.

## 3. Scope

- **C0** settings plumbing: six switches under Smart typing → Learning with the exact summaries in the lane table; `LearningPolicy` value object → `DecoderConfig` (bumps `configEpoch`); `VOCABULARY_POLICY_VERSION` 1→2 migration skeleton; Learned Words tab registration.
- **C1** backspace undo (C-F1) + correction vetoes (C-F1a) through the A0 `BackspaceHook`; window opens when the correction lands (not at separator press), discrete-key only, 5 s cap; `UndoWindow.open(source,target,separator,kind)` exposed to lane D independently of the `ti_learn_backspace_undo` switch.
- **C2** automatic word learning (C-F2): session-counted observations (≥60 min separation), near-dictionary explicit-signal guard, Observed/Provisional/Learned tiers, decay 14/45/180 d, 3000 cap, `LEARNED_FEEDBACK` once; third-use review dialog only when the switch is OFF; OFF-migration Keep/Remove dialog.
- **C3** typed-word keep/save (C-F3): quoted literal `KEEP_TYPED` → `SAVE_TYPED` → teach → `📖✓`.
- **C4** long-press remove (C-F4): in-strip confirm, unlearn or block-suggestion, `UNLEARNED_FEEDBACK`.
- **C5** personal next-word pairs (C-F5): `typing_model_bigrams`/`trigrams`, 5000/2000 caps, 90-day decay, rank above pack at ≥2 sessions.
- **C6** contact names (C-F6, default OFF): `READ_CONTACTS` runtime flow, `ContactNamesIndex` ≤5000 tokens, memory-only, recognized proper names.
- **C7** Learned Words tabs (Words w/ tier badges, Corrections, Word pairs, Blocked corrections, Blocked suggestions) + clear-all integration + DOX/`PRODUCT.md` updates.

## 4. Constraints (hard)

- **With every C switch OFF, behavior equals 2.0.119**: `KeyEventHandlerLearningContractTest`, `KeyEventHandlerAutocorrectContractTest`, `SuggestionPersonalizationTest` pass unchanged.
- C1 blocks D1b and D3 (undo window API) — encoded as machinery `depends_on`.
- C3/C4 need A0 roles/hooks and A1 tickets (`KEEP_TYPED`, `SAVE_TYPED`, `REMOVE_CONFIRM`, `candidate_long_pressed`).
- Never version bump / signed APK / phone upload — integrator/operator only.

## 5. Acceptance criteria

1. All-OFF regression suite green, unchanged (see §4).
2. `teh␣`→`the␣`, immediate ⌫ → `teh` (strip shows `teh`,`the`,…); second ⌫ deletes normally; cursor move/other key/5 s closes window; held-backspace repeats never undo; suggestion-tap replacements and D1b expansions undoable; late repairs not.
3. Vetoes: 1 context veto blocks auto-apply in that context; 2 vetoes block auto-apply everywhere while the target stays suggestible; vetoes visible/deletable in Learned Words.
4. Tiers: 3 uses/1 session → Observed; 3 uses/2 sessions ≥60 min apart → Provisional (suggested, protected, never autocorrect target); 6 uses/3 days → Learned; `teh`-class near-dictionary words need an explicit signal (undo veto, keep tap, D3 cycle-back, Teach) regardless of days; decay at 14/45/180 d via injected clock.
5. Edited and corrected-away words don't count; an undone correction counts.
6. Typed-word tap: first tap commits literal, second teaches; any new key cancels `SAVE_TYPED`.
7. Long-press: taught/auto/provisional words unlearn; dictionary words block; blocked word typed exactly is kept.
8. Next word: pair counted once/session, never across `. ! ?` or newline; personal pair outranks pack after 2 sessions; 90-day decay.
9. Contacts: permission denial reverts switch OFF; index bounded, memory-only, nothing persisted (asserted), names never autocorrected.
10. Decode p95 regression ≤10% under 3000 passive + 5000 pairs + 5000 contact tokens.
11. `PRODUCT.md` design-principles rewrite and the `srcs/juloo.keyboard2/AGENTS.md` + `suggestions/AGENTS.md` updates land in the same commit as their behaviors.

## 6. Changelog copy

Owned by `C-Adaptive-Learning.md` §10 (integrator assembles at release).

## 7. Dependencies

Machinery lanes `c-learn` (depends_on `a0-seams`) and `c-strip` (depends_on `a0-seams`, `a-strip`, `c-learn`) in `plan/PRD-FrankenKey-Typing-Intelligence-2026-09-27/crew-plan-typing-intelligence-2026-09-27.json`.
