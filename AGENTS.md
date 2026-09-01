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
- Unknown literal observations remain bounded and session-only. A Teach gesture always requires a positive choice; the third exact unknown literal commit in one safe editor session asks whether to keep the word, use the best safe recognized suggestion, or create an editable exact replacement. Only the explicit decision persists in credential-protected personalization.
- Reference evidence stays in `androidTest/assets/` with package/version/hash/input/outputs and separate full-reference vs physical spot-check scope.
- `ReaderActivity` and `Reader3dActivity` remain private. `ReaderShareActivity` accepts read-only `text/plain` Share/Process Text plus exact `application/epub+zip` ACTION_SEND and ACTION_VIEW from a granted `content://` URI. EPUB imports require a persisted user-selected Books tree with scoped read/write access: reference files already inside it, copy outside shares or opened documents into it once, and never request broad all-files access.
- EPUB Library rows retain source URI/tree state, metadata, canonical raw-word progress, and only a bounded derived cover thumbnail. Full chapter text/HTML is parsed transiently from the source with strict ZIP/XML/image bounds, sanitized rich markup, safe packaged images, and deterministic plain/raw-word mappings; never persist a duplicate book body.
- Reader AI is optional for safe URL articles, text explicitly loaded with Read Clipboard, and user-opened EPUB books. Nothing is sent on workspace open: before the first explicit request, disclose that selected source text/excerpts and questions go to OpenRouter/the selected model. Require the user-owned encrypted key, exclude it from backups, keep all parsing/networking native, and keep saved outputs/evidence private/local unless explicitly shared.

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
- `assets/AGENTS.md` — language/image/font assets and local 3D Reader surface.
- `fastlane/AGENTS.md` — store metadata/media.
- `vendor/AGENTS.md` — vendored code/data.
- `scripts/AGENTS.md` — generators/helpers.
- `doc/AGENTS.md` — durable docs.
