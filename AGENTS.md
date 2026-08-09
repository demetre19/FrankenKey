# FrankenKey Delivery DOX

## Purpose

- Own public release artifacts, documentation, product contracts, and APK archives.

## Ownership

- Root owns `README.md`, `CHANGELOG.md`, canonical APK, product/Reader plans, and release workflow.
- `../FrankenKey-autobuild-autocorrect/` owns Android source; `apk-backups/` owns archives/manifest.

## Local Contracts

- Keep artifacts understandable from the nearest DOX plus parents; closer docs specialize but never weaken this contract.
- Before edits, follow the indexed DOX chain. After meaningful changes, update affected purpose/ownership/contracts/workflow/indexes; omit updates only when behavior/contracts are unchanged.
- DOX sections: Purpose, Ownership, Local Contracts, Work Guidance, Verification, Child DOX Index. Keep them concise/current; document stable contracts, not history.

## Work Guidance

- `PRODUCT.md` owns durable product/user/design/accessibility context. `PRD-FrankenKey-Reader-2026-07-30.md` owns the approved Reader plan; `MACMINI-HANDOFF-PRD-FrankenKey-Reader-2026-07-30.md` owns resume state/commands.
- Inspect/execute supplied reference artifacts before parity work.
- Prefer programmatic paths; use UI only for UI behavior or unavailable programmatic surfaces.
- Re-check changed paths, DOX chains/indexes, stale text, and relevant verification at closeout.

## Release Contracts

- Canonical test/delivery path: `FrankenKey-installable-release.apk`; signed release variant only, package `dev.frankenkey.keyboard`, label `FrankenKey`, production logo, `verifyReleaseIdentity` pass. Never place debug/dev APKs in repository/updater paths.
- Test candidates stay local until explicit testing completion/publication approval. Push/tag/release only with separate approval.
- User-test builds must replace the canonical APK, then upload the exact bytes via direct Termux SSH port 8022 to `/storage/emulated/0/Download/FrankenKey-installable-release.apk`; verify remote size/SHA-256. Prefer `deme-s23-ultra.modem`, else online `Deme S23 Ultra` Tailscale IP. Never use localhost tunnels/Termux home; never install without approval.
- Releases use tag `v<versionName>-vc<versionCode>`, attach canonical APK, and publish behavior/outcome changelog text without comparison-brand names.
- Each release includes `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` consistent with its GitHub body.
- Published `README.md` version/hash/download metadata must match the exact APK and link `CHANGELOG.md`; keep about 10 major releases.
- New backup APKs require same-change `apk-backups/manifest.json` updates.

## Verification

- Updater baseline: installed 2.0.26/code 77 detected 2.0.27/code 78, showed changelog, authenticated APK, and updated in place; repeat when updater/delivery contracts change.

## Child DOX Index

- `apk-backups/AGENTS.md` — archive naming, identity metadata, and manifest rules.
