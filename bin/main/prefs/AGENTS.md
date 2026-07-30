# prefs DOX

## Purpose

- Own custom preference widgets and settings UI helpers.

## Ownership

- This folder owns reusable preference classes.
- `SettingsActivity` and `res/xml/settings.xml` own screen wiring.

## Local Contracts

- Preference widgets must reflect persisted config accurately and avoid surprising side effects.
- Color selector UI should use compact square chips beside labels per user preference.
- Dynamic extra-key checkboxes must use the shared concise summary explaining that the selected key is added only where absent.

## Work Guidance

- Keep preference behavior boring and Android-compatible.
- Update settings contract tests when keys, summaries, or placement change.

## Verification

- Run `SettingsUiContractsTest` or feature-specific settings tests for settings changes.

## Child DOX Index

- No child AGENTS.md files currently. Preference widgets are owned here.
