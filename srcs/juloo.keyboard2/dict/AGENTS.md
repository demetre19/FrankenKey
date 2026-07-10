# dict DOX

## Purpose

- Own dictionary list/loading UI and supported dictionary metadata.

## Ownership

- This folder owns dictionary-domain Java classes.
- Vendored dictionary engines/data live under `vendor/` and `assets/`.

## Local Contracts

- Dictionary metadata must match available language assets and supported downloads.
- Do not break offline/local typing behavior when changing dictionary integration.

## Work Guidance

- Keep download/list UI separate from scoring/ranking logic.
- Preserve user dictionaries and personalization behavior.

## Verification

- Run focused dictionary/language-pack tests when dictionary behavior changes.

## Child DOX Index

- No child AGENTS.md files currently. Dictionary classes are owned here.
