# apk-backups DOX

## Purpose

- Preserve installable FrankenKey APK backups across release iterations.
- Keep enough metadata to identify package, version, hash, signer, source, and release purpose.

## Ownership

- This folder owns archived APK files and `manifest.json`.
- The project root owns the current `FrankenKey-installable-release.apk`; this folder owns historical copies.

## Local Contracts

- Every APK added here must have a corresponding `manifest.json` entry.
- Backup filenames must include version name, version code, signer hint, and a short APK SHA/hash suffix when available.
- Manifest entries must record package, versionCode, versionName, size, sha256, signerSha256, source, label, and a concise note.
- Do not overwrite or delete old signer/debug-key backups unless the user explicitly asks.

## Work Guidance

- Treat release-signing and signer compatibility as durable metadata, not incidental notes.
- Keep backup notes factual and short; describe what makes the release worth preserving.
- Prefer adding a new backup over mutating old APK artifacts.

## Verification

- For new backups, verify the copied APK hash against the manifest `sha256`.
- When possible, verify package/version/signature with Android build tools before recording metadata.

## Child DOX Index

- No child AGENTS.md files currently. This folder is a single release-backup boundary.
