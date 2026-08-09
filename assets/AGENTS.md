# assets DOX

## Purpose

- Own packaged/source assets: language packs, icons, fonts, and licenses.

## Ownership

- `assets/` owns sources; generated runtime copies belong to `res/`.

## Local Contracts

- Preserve source images unless explicitly replaced; avoid needless binary rewrites.
- Keep third-party notices in `assets/licenses/`, including Lucide ISC/MIT notice.
- Bundle matching `en_AU`, `en_GB`, and `en_US` Cdict (`dictionaries/`) and Hunspell plus generated 4,096-row short-word n-gram (`language_packs/`) assets with pinned sources, hashes, licenses, and notices.
- English Hunspell additions `gboard`, `omp`, and `cmux` require synchronized hashes/disclosure in `licenses/english-hunspell-source.txt`.

## Work Guidance

- Keep source icons distinct from generated launcher/resources.

## Verification

- Run focused dictionary/language-pack tests for typing data; verify image/font consumers after asset changes.

## Child DOX Index

- None.
