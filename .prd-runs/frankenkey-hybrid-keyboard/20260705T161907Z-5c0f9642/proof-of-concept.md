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


## T002 — Local-only snippet storage

Status: done  
Recorded: `2026-07-05T17:44:59Z`

Evidence:

- Added `srcs/juloo.keyboard2/snippets/SnippetStore.java`.
- Added focused JUnit test `test/juloo.keyboard2/snippets/SnippetStoreTest.java`.
- Added test-only dependency `org.json:json:20240303` so Android JSON storage code can run under local JVM unit tests.
- Verified:
  - `./gradlew testDebugUnitTest --tests juloo.keyboard2.snippets.SnippetStoreTest --rerun-tasks`
  - Result: `BUILD SUCCESSFUL`.
- Covered contracts:
  - JSON roundtrip preserves list order, index, phrase, custom label, and icon-label flag;
  - empty slots remain visible after save/load;
  - null and malformed JSON return fixed visible empty slots up to requested minimum;
  - default slot count is `SnippetStore.DEFAULT_SLOT_COUNT`;
  - `replaceSlot` updates by index without reordering surrounding slots;
  - encoded JSON uses local slot fields only and no URL/network/account/cloud-style fields.

Decision: local-only storage model is complete. Continue to runtime row and insertion integration.


## P002 — Additive row layout seam

Status: passed  
Recorded: `2026-07-05T17:44:59Z`

Evidence:

- Added focused static layout test `test/juloo.keyboard2/snippets/KeyboardLayoutSeamTest.java`.
- Verified:
  - `./gradlew testDebugUnitTest --tests juloo.keyboard2.snippets.KeyboardLayoutSeamTest --rerun-tasks`
  - Result: `BUILD SUCCESSFUL`.
- The test parses `res/layout/keyboard.xml` and proves:
  - root remains a vertical `LinearLayout`;
  - `juloo.keyboard2.Keyboard2View` remains a direct child;
  - `Keyboard2View` keeps id `@+id/keyboard_view`, which `Keyboard2.create_keyboard_view()` uses for runtime lookup;
  - there is a direct sibling insertion position before `Keyboard2View` for the additive snippet row.

Decision: the row seam is proven without touching `Keyboard2View` or baseline key hit-testing code. Continue to paged snippet row implementation.


## T004 — Runtime paged snippet row

Status: done  
Recorded: `2026-07-05T17:51:18Z`

Evidence:

- Added `srcs/juloo.keyboard2/snippets/SnippetRowView.java`.
- Inserted `SnippetRowView` as a direct sibling immediately above `Keyboard2View` in `res/layout/keyboard.xml`.
- Updated `Keyboard2.create_keyboard_view()` to bind `R.id.snippet_row` and `Keyboard2.refresh_config()` to call `_snippet_row_view.refresh_config(_prefs, null)`.
- Updated `test/juloo.keyboard2/snippets/KeyboardLayoutSeamTest.java` to defend the runtime row seam and shared page-size contract.
- Verified:
  - `./gradlew compileDebugJavaWithJavac` — `BUILD SUCCESSFUL`.
  - `./gradlew testDebugUnitTest --tests juloo.keyboard2.snippets.KeyboardLayoutSeamTest --rerun-tasks` — `BUILD SUCCESSFUL`.
- Runtime row behavior implemented:
  - visible whenever `SnippetStore.PREF_ENABLED` is true/default;
  - loads local-only slots from `SnippetStore`;
  - renders seven controls per page via `SnippetPages.PAGE_SIZE`;
  - horizontally scrolls and snaps to page-width groups;
  - shows empty slot labels as visible disabled/low-alpha controls;
  - leaves `Keyboard2View` as the same direct child/id and does not modify its touch/key handling.

Decision: runtime row implementation is complete. Continue to exact snippet insertion proof/wiring.


## P004 — Exact snippet insertion helper

Status: passed  
Recorded: `2026-07-05T17:55:08Z`

Evidence:

- Added `srcs/juloo.keyboard2/snippets/SnippetInserter.java`.
- Added focused JUnit test `test/juloo.keyboard2/snippets/SnippetInsertionTest.java`.
- Verified:
  - `./gradlew testDebugUnitTest --tests juloo.keyboard2.snippets.SnippetInsertionTest --rerun-tasks`
  - Result: `BUILD SUCCESSFUL`.
- Covered contracts:
  - `InputConnection.commitText` receives exactly the configured phrase;
  - cursor position argument is `1`;
  - no trailing space is appended;
  - null `InputConnection` is safe and returns `false`.

Decision: exact phrase insertion helper proof is complete.


## T005 — Snippet tap insertion wiring

Status: done  
Recorded: `2026-07-05T17:55:08Z`

Evidence:

- Added `KeyEventHandler.snippet_entered(String phrase)` delegating to existing `send_text(phrase)`.
- Updated `send_text` to use `SnippetInserter.insert(conn, text)` after existing autocapitalisation/current-word updates, preserving the same `commitText(text, 1)` text path.
- Updated `Keyboard2.refresh_config()` to pass a non-null `SnippetRowView` listener:
  - `_keyeventhandler.snippet_entered(slot.getPhrase())`.
- Focused insertion test verifies the row listener inserts `slot.getPhrase()` and does not add suggestion-style whitespace.

Decision: tapping a configured snippet row slot is wired to exact phrase insertion and keeps the keyboard path active.
