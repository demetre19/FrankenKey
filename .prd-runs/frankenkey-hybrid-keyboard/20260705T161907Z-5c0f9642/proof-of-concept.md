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


## T001 — Baseline import and FrankenKey identity checkpoint

Status: done  
Recorded: `2026-07-05T17:24:14Z`

Evidence:

- Imported Unexpected Keyboard upstream commit `9c70f1113e3217c744f0618d872e1be3cc68b1c0` into the project source tree.
- Foundation branch commit: `7802453`.
- Main merge commit: `ffd8af6`.
- Preserved upstream namespace/source package `juloo.keyboard2` and baseline implementation structure to avoid unnecessary bottom-keyboard churn.
- Changed only app/build identity required for FrankenKey:
  - Gradle `applicationId` base: `dev.frankenkey.keyboard`.
  - Root project name: `FrankenKey`.
  - App labels: `FrankenKey` / `FrankenKey (Debug)`.
- Rebuilt debug APK after identity changes: `BUILD SUCCESSFUL`.
- APK metadata proof from `aapt2 dump badging`:
  - package: `dev.frankenkey.keyboard.debug`.
  - application label: `FrankenKey (Debug)`.
  - IME component advertised: `provides-component:'ime'`.

Decision: baseline source and FrankenKey identity checkpoint are complete. Continue to snippet model proof before storage/editor/runtime implementation.


## P003 — Snippet model labels and seven-slot grouping

Status: passed  
Recorded: `2026-07-05T17:29:10Z`

Evidence:

- Added production model classes:
  - `srcs/juloo.keyboard2/snippets/SnippetSlot.java`
  - `srcs/juloo.keyboard2/snippets/SnippetPages.java`
- Added focused JUnit test: `test/juloo.keyboard2/snippets/SnippetSlotTest.java`.
- Verified with external JDK 17 and a run-local `python` shim for Gradle generation tasks:
  - `./gradlew testDebugUnitTest --tests juloo.keyboard2.snippets.SnippetSlotTest`
  - Result: `BUILD SUCCESSFUL`.
- Covered contracts:
  - empty slot display labels are one-based slot indexes;
  - phrase labels use the first one or two Unicode code points without splitting surrogate pairs;
  - non-empty custom labels override generated labels;
  - configured state follows a non-empty insertable phrase;
  - page size is exactly seven;
  - slot count boundaries `0`, `1`, `7`, `8`, `14`, and `15` produce expected page counts and page slices.

Decision: snippet model boundary proof is complete. Continue to local-only snippet storage implementation.
