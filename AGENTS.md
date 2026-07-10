# FrankenKey Android Source DOX

## Purpose

- Own the Android source checkout for FrankenKey: keyboard app code, resources, tests, build configuration, assets, vendor code, release metadata, and helper scripts.
- Keep source work reproducible from this DOX tree without relying on chat history or memory.

## Ownership

- This root owns project-wide Android build/release workflow, Gradle files, manifest, top-level scripts, and cross-cutting source conventions.
- The sibling `../FrankenKey` folder owns delivered APK artifacts and public release packaging.
- Child AGENTS.md files own local contracts for durable subtrees listed below.

## Local Contracts

- Package is `dev.frankenkey.keyboard`.
- Release builds use the persistent local release key loaded from `/Users/apple/.android/frankenkey-release-signing/frankenkey-release.env`; do not print secret values.
- Current installable APK must be copied to `../FrankenKey/FrankenKey-installable-release.apk` before asking the user to test a build.
- When producing release APKs, update `../FrankenKey/apk-backups/manifest.json` and archive a backup when the release should be preserved.
- Android source/resource changes must preserve both clean Fleksy-style everyday mode and optional dense FrankenKey mode unless the user explicitly narrows scope.
- In-app update checks use the public GitHub latest-release API only from unlocked launcher/settings activities. Releases use tag `v<versionName>-vc<versionCode>`, attach `FrankenKey-installable-release.apk`, and place user-visible changelog text in the release body.
- Every published version must include `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`; keep its user-facing message consistent with the GitHub Release body.
- Automatic update checks default on, but download and installer handoff always require explicit user acceptance. Update APKs must match the package, a higher version code, the declared SHA-256/size, and the persistent release signer before Android receives them.
- Treat clipboard/snippets/personalization data as user data; preserve privacy and avoid unnecessary persistence.
- Suggestion display and autocorrect share one bounded asynchronous decoder; never perform dictionary/Hunspell decoding or wait for results on the IME main thread.

## Work Guidance

- Match existing Java and XML style; make surgical changes.
- Use focused unit tests for touched behavior; do not run broad suites unless necessary.
- Use `JAVA_HOME=/opt/homebrew/opt/openjdk@17` for Gradle commands.
- Android SDK tools may live at `/Volumes/TheHoneyBadger/AndroidTooling/android-sdk`.
- Prefer existing app patterns over new frameworks or abstractions.
- For live phone testing, use the saved RustDesk/Termux SSH workflow; Termux SSH is unprivileged and cannot run privileged Android shell commands.

## Verification

- Focused tests: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew --no-daemon --no-configuration-cache testDebugUnitTest --tests <TestClass>`.
- Release build: load release signing env without echoing secrets, then run `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew --no-daemon --no-configuration-cache assembleRelease`.
- Verify release APK with Android build tools when relevant: package/version via `aapt2`, signature via `apksigner`, and SHA-256 via `shasum -a 256`.
- Local emulator AVD is `FrankenKeyParity`; emulator binary is `/Volumes/TheHoneyBadger/AndroidTooling/android-sdk/emulator/emulator`.
- When updater or release-delivery contracts change, repeat an installed-old-version to published-new-version device test covering announcement, authenticated download, Android installer handoff, and successful in-place update.

## Child DOX Index

- `srcs/AGENTS.md` — generated/source-input tree and main Java package source boundaries.
- `res/AGENTS.md` — Android packaged resources, layouts, strings, icons, raw media, and localization.
- `test/AGENTS.md` — Robolectric/unit test contracts and focused behavior coverage.
- `assets/AGENTS.md` — language packs and source image/font assets.
- `fastlane/AGENTS.md` — store metadata, screenshots, icon metadata, and listing assets.
- `vendor/AGENTS.md` — vendored Hunspell/cdict code and third-party source boundaries.
- `scripts/AGENTS.md` — helper scripts for generated data/resources.
- `doc/AGENTS.md` — user/developer docs inherited from the keyboard project.
