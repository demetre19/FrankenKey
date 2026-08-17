# lang DOX

## Purpose

- Own language-pack models, parsing, loading, and management.

## Ownership

- This folder owns Java logic; `assets/language_packs/` owns pack files.

## Local Contracts

- Loading stays deterministic/local; malformed, oversized, or missing packs fail visibly, never corrupt suggestions.
- `en_AU`, `en_GB`, and `en_US` subtypes resolve only to matching bundled Hunspell data and the bounded corpus-derived English bigram/trigram model.

## Work Guidance

- Keep parsing explicit; coordinate asset and test changes.

## Verification

- Run focused language-pack/dictionary tests.

## Child DOX Index

- None.
