# Proof of Concept Plan

Run: `20260705T161907Z-5c0f9642`  
Updated: `2026-07-05T16:55:05Z`

No production implementation may start for the related item until its proof is complete and recorded here.

| Proof ID | Assumption | Minimal proof | Required before |
|---|---|---|---|
| P001 | Unexpected Keyboard can be imported and built from source in this environment | Clone/import upstream with submodules, resolve SDK/tooling, run the narrow debug build command or record exact blocker | Baseline source/identity work |
| P002 | Snippet row can be added above the keyboard without changing baseline key hit testing | Local source-level proof after import: add/wrap a non-production row or inspect/measure layout seam; verify Keyboard2View still receives baseline touches in focused test/smoke | Runtime snippet row implementation |
| P003 | Snippet data model supports Fleksy-like labels and groups of seven | Unit test or small Java/Python fixture for slot fields, label generation, and page grouping boundaries 0/1/7/8/14/15 | Storage/editor/runtime row work |
| P004 | Snippet insertion commits exact phrase and keeps keyboard open | Unit/instrumentation seam around `InputConnection.commitText(phrase, 1)` or focused IME smoke | Insertion hook integration |
| P005 | FrankenKey identity change does not break manifest/method metadata/debug signing | Build/debug install proof after package/app rename | Final APK and install verification |


## P001 — Upstream import and debug build path

Status: passed  
Recorded: `2026-07-05T17:18:44Z`

Evidence:

- Installed JDK 17.0.19 under `/Volumes/TheHoneyBadger/AndroidTooling/jdks/jdk-17.0.19+10/Contents/Home`.
- Installed Android SDK packages under `/Volumes/TheHoneyBadger/AndroidTooling/android-sdk`:
  - `cmdline-tools;latest`
  - `platform-tools`
  - `platforms;android-36`
  - `build-tools;36.0.0`
  - `ndk;29.0.14206865`
  - `emulator`
- Cloned Unexpected Keyboard upstream with submodule `vendor/cdict`.
- Upstream commit: `9c70f1113e3217c744f0618d872e1be3cc68b1c0`.
- Copied upstream source into foundation worktree.
- Ran `./gradlew --version` successfully with external `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT`.
- Ran `./gradlew assembleDebug` successfully.
- Build output: `.prd-runs/frankenkey-hybrid-keyboard/20260705T161907Z-5c0f9642/worktrees/foundation/build/outputs/apk/debug/Unexpected-Keyboard-debug.apk`.

Decision: source import/build path is proven. Continue to identity changes and baseline source checkpoint.
