# PRD: FrankenKey Typing Intelligence — Section D: Power Typing (Shortcuts, Touch Calibration, Correction Cycling, Inline Completion)

Date: 2026-09-27 (launch 2026-09-28)
Status: **Approved** — behaviors 7–10 as standard features (2026-09-27); Q1 default adopted (no separate opt-ins; follows master Suggestions/Autocorrect switches; inline completion follows Suggestions); Q3 default adopted (contextual near-vertical swipe-down inside the undo window; space-swipe accept dropped). Launch authorized 2026-09-28.
Source repo: `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect`, base `autobuild/frankenkey-autocorrect-suggestions` @ `57f1356`.

## 1. Goal

Four behaviors that make fast typists faster: personal text shortcuts, a visible/resettable adaptive-touch surface, space-bar correction cycling inside the undo window, and one-tap strip completion.

## 2. Canonical specification

`plan/PRD-FrankenKey-Typing-Intelligence-2026-09-27/D-Power-Typing.md` is binding (D-F1 through D-F4, stages D0–D4, tests, DOX lines, changelog). Overview principles 1–3, 9, §5.3 bind.

## 3. Scope

- **D1a** `ShortcutStore` (`text_shortcuts` named prefs, versioned JSON, ≤300 entries, trigger 1–16 `[A-Za-z0-9']`, expansion ≤500 UTF-16) + management UI (add/edit/delete/search; starter examples as placeholder hints only) + `SettingsBackup` export/import with clash confirmation.
- **D1b** `ShortcutCandidateSource` + tap expansion + automatic separator expansion (only with Autocorrect ON, prose-eligible editors), always through C1's `UndoWindow` (works even when `ti_learn_backspace_undo` is OFF); never in email/URI/password/terminal or inside an email token; no C evidence.
- **D2** read-only `touch_calibration_stats()` + Settings status row + **Reset touch zones** (clears only `typing_model_touch_*`) + training exclusions (email tokens, shortcut triggers, undone corrections).
- **D3** space-bar correction cycling via the A0 `SpaceGestureHook`: only inside C1's undo window, only a near-vertical raw down (S ±1 of 16); cycles `target → alts → source → target` ≤6 entries with per-step verify + batch replace + strip highlight; cycling to the literal records a C1 veto; outside the window everything resolves exactly as today (dense `switch_backward`, clean corner snap); diagonals keep corner actions inside the window; layout XML not edited.
- **D4** `CompletionPolicy` (prefix ≥2, ≥2 longer, not literal, `COMPLETION_MARGIN_Q8` = 1.6× runner-up margin, prefix not a frequent complete word) + `COMPLETION` role rendering (dimmed suffix, accessibility "Complete to X") + tap-only accept; never auto-applies on space; no in-editor ghost text.

## 4. Constraints

- D1b and D3 are hard-gated on C1's undo window — encoded as machinery `depends_on` on `c-learn`.
- D2/D4 need A0 seams/roles (`CandidateSource`, `COMPLETION` render hook, Settings sub-screen).
- D keeps `PersonalizationStore` edits read-only-additive (stats accessor + touch keys only); C owns all other personalization keys.
- Never version bump / signed APK / phone upload — integrator/operator only.

## 5. Acceptance criteria

1. Store: trigger validation incl. dictionary-word warn-confirm; 301st insert rejected; JSON round-trip; corrupt JSON safe; export/import merge with listed clash confirmation.
2. `omw␣` expands to `On my way! ␣` only with Autocorrect ON; ⌫ restores `omw` even when C's undo switch is OFF; tap expansion works with Autocorrect OFF; a trailing-newline expansion adds no space; no expansion in email/URI/password/terminal/email-token contexts.
3. Calibration row shows pre-20 "learning" and post-20 "active" states; reset clears only touch keys; email/shortcut/undone-correction touches are never sampled.
4. Cycling: full order and wrap to target; literal cycle records a veto; inside the window SW/SE diagonals still open emoji/GIF; outside, `switch_backward` fires in dense and the corner snap is unchanged in clean; any other key closes the window.
5. Completion: margin gate; frequent-complete-word prefix → none; tap accepts with space; space-bar swipes unchanged with or without a completion; replay-corpus precision ≥95%.
6. `srcs/juloo.keyboard2/AGENTS.md` shortcut/cycle/completion bullet + `suggestions/AGENTS.md` `COMPLETION`/`CompletionPolicy`/`ShortcutCandidateSource` precedence lines land same-commit.

## 6. Changelog copy

Owned by `D-Power-Typing.md` §8 (integrator assembles at release).

## 7. Dependencies

Machinery lanes `d-power-store` (depends_on `a0-seams`) and `d-power-gestures` (depends_on `a-strip`, `c-learn`) in `plan/PRD-FrankenKey-Typing-Intelligence-2026-09-27/crew-plan-typing-intelligence-2026-09-27.json`.
