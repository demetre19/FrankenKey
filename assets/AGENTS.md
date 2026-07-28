# assets DOX

## Purpose

- Own source assets packaged or used by FrankenKey: language packs, source icons, and font assets.

## Ownership

- This folder owns asset files under `assets/`.
- Runtime resource copies generated from assets are owned by `res/`.

## Local Contracts

- Preserve source image assets unless explicitly replacing them.
- Language pack assets are user-facing typing data; changes require focused verification of lookup/loading behavior.
- Third-party assets must keep their applicable license notice under `assets/licenses/`; the Lucide snippet icons require the packaged ISC/MIT notice.
- Bundled `en_AU`, `en_GB`, and `en_US` Cdict files live under `assets/dictionaries/`; matching Hunspell packs live under `assets/language_packs/`. Keep their pinned source, hashes, and applicable notices under `assets/licenses/`.
- The English Hunspell `.dic` files add the lowercase technical names `gboard`, `omp`, and `cmux`; keep `licenses/english-hunspell-source.txt` hashes and modification disclosure synchronized with those files.

## Work Guidance

- Avoid recompressing or rewriting binary assets without need.
- Keep icon source assets distinct from generated launcher/resource outputs.

## Verification

- For language packs, run focused dictionary/language-pack tests when changed.
- For image/font assets, verify generated/resource consumers when relevant.

## Child DOX Index

- No child AGENTS.md files currently. Asset subfolders are owned here.
