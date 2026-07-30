# lang DOX

## Purpose

- Own language-pack model and loading/manager logic.

## Ownership

- This folder owns language-pack Java classes.
- Pack files live under `assets/language_packs`.

## Local Contracts

- Language-pack loading must stay deterministic and local.
- Missing or malformed packs should fail loud enough for tests/users, not silently corrupt suggestions.
- The advertised `en_AU` and `en_GB` subtypes must each resolve to their matching bundled Hunspell pack; do not fall back to US spelling data.

## Work Guidance

- Keep parsing/loading code small and explicit.
- Coordinate asset changes with tests.

## Verification

- Run focused language-pack/dictionary tests when changed.

## Child DOX Index

- No child AGENTS.md files currently. Language-pack classes are owned here.
