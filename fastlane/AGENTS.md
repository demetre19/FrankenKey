# fastlane DOX

## Purpose

- Own Android store metadata, screenshots, icons, and listing assets used for distribution.

## Ownership

- This folder owns `fastlane/metadata/android` and related store-facing media.
- App runtime resources live under `res/`.

## Local Contracts

- Do not overwrite store icon/screenshots unless the user explicitly requests store asset changes.
- Keep metadata truthful to the shipped APK behavior and version.
- Preserve upstream/Fleksy attribution language where present.
- Every release adds `metadata/android/en-US/changelogs/<versionCode>.txt`; its user-visible message must agree with the corresponding GitHub Release body.

## Work Guidance

- Treat store screenshots as release artifacts; verify visuals after replacement.
- Avoid broad metadata rewrites for code-only changes.

## Verification

- For screenshot/image changes, visually inspect rendered assets.
- For listing copy, read back the exact changed file.

## Child DOX Index

- No child AGENTS.md files currently. Fastlane metadata is owned here.
