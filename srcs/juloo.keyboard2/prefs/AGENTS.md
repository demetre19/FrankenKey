# prefs DOX

## Purpose

- Own reusable preference widgets/settings helpers.

## Ownership

- This folder owns widgets; `SettingsActivity` and `res/xml/settings.xml` own screen wiring.

## Local Contracts

- Reflect persisted config exactly; avoid hidden side effects.
- Use compact square color chips beside labels.
- Dynamic extra-key checkboxes use the shared concise “added only where absent” summary.
- The extra-key catalog excludes keys already served by the Extra Keys panel (Esc, Tab, arrows, Home/End/PgUp/PgDn, Ins, Del, F1–F12, Ctrl, Alt, Shift, Meta/Cmd); those stay panel-only so the typing surface stays clean. Layout corner gestures for them use `hide` (invisible, always active) rather than `loc`.
- Extra Keys manager persists visibility/order, shows row drag handles, and accepts bounded supported Ctrl/Alt/Shift/Cmd key/command combinations.
- Learned Words remains a separate searchable list with Taught and Corrections tabs. Direct teaching validates one word; Corrections exposes editable source-to-fixed-target or source-to-best-safe rules plus deliberate adaptive pairs. Per-row forgetting/deletion requires confirmation.

## Work Guidance

- Stay conventional and update settings-contract tests with keys, summaries, or placement.

## Verification

- Run `SettingsUiContractsTest` and relevant feature settings tests.

## Child DOX Index

- None.
