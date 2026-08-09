# prefs DOX

## Purpose

- Own reusable preference widgets/settings helpers.

## Ownership

- This folder owns widgets; `SettingsActivity` and `res/xml/settings.xml` own screen wiring.

## Local Contracts

- Reflect persisted config exactly; avoid hidden side effects.
- Use compact square color chips beside labels.
- Dynamic extra-key checkboxes use the shared concise “added only where absent” summary.
- Extra Keys manager persists visibility/order, shows row drag handles, and accepts bounded supported Ctrl/Alt/Shift/Cmd key/command combinations.
- Learned Words remains a separate searchable list; direct teaching validates one word and per-row forgetting requires confirmation.

## Work Guidance

- Stay conventional and update settings-contract tests with keys, summaries, or placement.

## Verification

- Run `SettingsUiContractsTest` and relevant feature settings tests.

## Child DOX Index

- None.
