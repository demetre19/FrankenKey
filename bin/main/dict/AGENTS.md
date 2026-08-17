# dict DOX

## Purpose

- Own dictionary metadata, list UI, installation, and loading.

## Ownership

- This folder owns dictionary Java; engines/data belong to `vendor/` and `assets/`.

## Local Contracts

- Metadata must match local/downloadable assets; preserve offline typing.
- Bundle and always list separate `en_AU`, `en_GB`, `en_US` Cdict assets. Independent generation seed markers add updates without restoring user-removed dictionaries; missing/invalid installed files self-repair locally.
- Installed selections are credential-protected. Pre-unlock loads fail without caching absence, then retry after unlock.

## Work Guidance

- Keep list/download UI outside scoring; preserve user dictionaries/personalization.

## Verification

- Run focused dictionary/language-pack tests.

## Child DOX Index

- None.
