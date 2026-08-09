# APK Archive DOX

## Purpose

- Preserve identifiable FrankenKey APK releases.

## Ownership

- `apk-backups/` owns archived APKs and `manifest.json`; root owns current canonical APK.

## Local Contracts

- Every added APK requires a same-change manifest entry.
- Filenames include version name/code, signer hint, and short APK hash when available.
- Entries record label, file, source, package, versionCode/versionName, size, SHA-256, signer SHA-256, and concise note.
- Never overwrite/delete old signer or debug-key backups without explicit approval.

## Work Guidance

- Prefer adding immutable backups; keep signing compatibility metadata factual and notes short.

## Verification

- Compare archive hash to manifest; verify package/version/signature when tools are available.

## Child DOX Index

- None.
