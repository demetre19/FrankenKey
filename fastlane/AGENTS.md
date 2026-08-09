# fastlane DOX

## Purpose

- Own Android store metadata and listing media.

## Ownership

- `fastlane/metadata/android/` owns listing copy/media; runtime resources belong to `res/`.

## Local Contracts

- Never replace store icons/screenshots without explicit approval.
- Metadata must match shipped behavior/version and preserve attribution.
- Every release adds `en-US/changelogs/<versionCode>.txt` consistent with its GitHub Release body.

## Work Guidance

- Verify visual assets; avoid broad metadata rewrites for code-only changes.

## Verification

- Inspect changed images; read back listing copy.

## Child DOX Index

- None.
