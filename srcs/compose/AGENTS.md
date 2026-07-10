# compose DOX

## Purpose

- Own compose/dead-key, shift, fn, and numpad JSON source data.

## Ownership

- This folder owns JSON input data consumed by compose/key generation.
- Generated Java/resource artifacts are owned by their output subtree.

## Local Contracts

- Preserve valid JSON and existing key naming conventions.
- Keep compose behavior deterministic and locale-aware.

## Work Guidance

- Use generator scripts rather than hand-editing generated outputs.
- Keep additions minimal and sorted/grouped with nearby existing data where applicable.

## Verification

- Run the relevant compose generation/check path when data changes.
- Run focused compose/key tests if behavior is covered.

## Child DOX Index

- No child AGENTS.md files currently. Compose data is owned here.
