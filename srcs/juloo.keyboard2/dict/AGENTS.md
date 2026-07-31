# dict DOX

## Purpose

- Own dictionary list/loading UI and supported dictionary metadata.

## Ownership

- This folder owns dictionary-domain Java classes.
- Vendored dictionary engines/data live under `vendor/` and `assets/`.

## Local Contracts

- Dictionary metadata must match available language assets and supported downloads.
- Do not break offline/local typing behavior when changing dictionary integration.
- Australian (`en_AU`), UK (`en_GB`), and US (`en_US`) production Cdict assets are bundled. AU/UK and US use separate once-per-generation seed markers so updates add new assets without restoring dictionaries the user removed. If a bundled dictionary remains marked installed but its private file is missing or invalid, loading repairs it from the bundled asset. The dictionary manager must always show all three English options and reinstall them from local assets without requiring Internet access.
- Installed dictionary selections are credential-protected durable state. If the IME process starts before credential storage is available, dictionary loading must fail without caching a missing result and retry persisted state after unlock so reboot does not make installed dictionaries appear absent.

## Work Guidance

- Keep download/list UI separate from scoring/ranking logic.
- Preserve user dictionaries and personalization behavior.

## Verification

- Run focused dictionary/language-pack tests when dictionary behavior changes.

## Child DOX Index

- No child AGENTS.md files currently. Dictionary classes are owned here.
