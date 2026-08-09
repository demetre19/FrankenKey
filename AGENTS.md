# FrankenKey Android Source DOX

## Purpose

- Own Android source, resources, tests, build/release configuration, assets, vendor code, and metadata.

## Ownership

- This root owns build/release wiring; `../FrankenKey` owns delivered artifacts/public packaging; children own indexed domains.

## Local Contracts

- Package stays `dev.frankenkey.keyboard`; user identity is FrankenKey with production icons.
- Release signing loads `/Users/apple/.android/frankenkey-release-signing/frankenkey-release.env` without printing secrets. Never deliver debug/dev APKs.
- User-test builds require signed `assembleRelease`, `verifyReleaseIdentity`, and copy to `../FrankenKey/FrankenKey-installable-release.apk`.
- Preserve release APKs in `../FrankenKey/apk-backups/manifest.json` when required.
- Keep clean everyday and dense coding modes unless scope explicitly narrows.
- Update checks use GitHub latest release only from unlocked launcher/Settings. Tags are `v<versionName>-vc<versionCode>`; releases attach `FrankenKey-installable-release.apk` and consistent Fastlane/GitHub changelogs.
- Update checks default on; downloads/installers require acceptance and validate package, higher code, declared hash/size, and release signer.
- Clipboard/snippets/personalization remain private/local.
- Suggestions/autocorrect share one bounded async decoder; never decode/wait on IME main thread.
- Reference evidence stays in `androidTest/assets/` with package/version/hash/input/outputs and separate full-reference vs physical spot-check scope.
- `ReaderActivity` remains private; only `ReaderShareActivity` accepts read-only `text/plain` Share/Process Text through opaque handoff.

## Work Guidance

- Match Java/XML style; prefer existing patterns and focused changes/tests.
- Gradle uses `JAVA_HOME=/opt/homebrew/opt/openjdk@17`; `/Users/apple/.local/bin/python` must resolve to Python 3. SDK may be `/Volumes/TheHoneyBadger/AndroidTooling/android-sdk`.
- Phone delivery uses direct Termux SSH `deme-s23-ultra.modem:8022` (or online Tailscale IP), never localhost tunnel.

## Verification

- Focused test: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew --no-daemon --no-configuration-cache testDebugUnitTest --tests <TestClass>`.
- Release: load signing env; run `assembleRelease`/`verifyReleaseIdentity`; verify package/version/signature/SHA-256 with Android tools.
- Throughput changes require focused and chapter `RealImeThroughputInstrumentedTest` at zero artificial interval.
- Updater changes require old-installed → published-new announcement, authenticated download, installer, and in-place update.
- Emulator: `FrankenKeyParity`; binary `/Volumes/TheHoneyBadger/AndroidTooling/android-sdk/emulator/emulator`.

## Child DOX Index

- `srcs/AGENTS.md` — source inputs/app code.
- `res/AGENTS.md` — packaged resources.
- `test/AGENTS.md` — unit/Robolectric tests.
- `assets/AGENTS.md` — language/image/font assets.
- `fastlane/AGENTS.md` — store metadata/media.
- `vendor/AGENTS.md` — vendored code/data.
- `scripts/AGENTS.md` — generators/helpers.
- `doc/AGENTS.md` — durable docs.
