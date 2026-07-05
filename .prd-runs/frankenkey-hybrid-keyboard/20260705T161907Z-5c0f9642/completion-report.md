# Completion Report

Run: `20260705T161907Z-5c0f9642`  
Status: complete; implementation, privacy hardening, final CLI smoke, and independent acceptance/adversarial re-review all passed.

## V001 Baseline Unexpected Keyboard behavior

Status: `passed`  
Recorded: `2026-07-05T19:05:46Z`

Evidence:

- Final `./gradlew testDebugUnitTest` completed with `BUILD SUCCESSFUL` after all snippet, privacy, migration, and secure-gate fixes.
- The full unit suite includes existing baseline compose/key modifier coverage and the new snippet/privacy tests.
- Earlier transient baseline failure in `ComposeKeyTest.spaceKey` / `KeyModifierTest.compose` exposed nondeterministic compose input ordering. Fixed at source in `srcs/compose/compile.py` so `extra.json` is processed deterministically before the XKB compose directory, preserving the documented `space space -> nbsp` override.
- Generated `srcs/juloo.keyboard2/ComposeKeyData.java` is not left modified after the full suite.

## V002 Snippet editor/storage/grouping/insertion behavior

Status: `passed`  
Recorded: `2026-07-05T19:05:46Z`

Focused tests that passed:

- `./gradlew testDebugUnitTest --tests juloo.keyboard2.snippets.SnippetSlotTest`
- `./gradlew testDebugUnitTest --tests juloo.keyboard2.snippets.SnippetStoreTest --rerun-tasks`
- `./gradlew testDebugUnitTest --tests juloo.keyboard2.snippets.KeyboardLayoutSeamTest --rerun-tasks`
- `./gradlew testDebugUnitTest --tests juloo.keyboard2.snippets.SnippetInsertionTest --rerun-tasks`
- `./gradlew testDebugUnitTest --tests juloo.keyboard2.snippets.SnippetSettingsTest --rerun-tasks`
- `./gradlew testDebugUnitTest --tests juloo.keyboard2.EditorConfigTest --tests juloo.keyboard2.snippets.SnippetStoreTest --tests juloo.keyboard2.DirectBootAwarePreferencesTest --tests juloo.keyboard2.snippets.KeyboardLayoutSeamTest --tests juloo.keyboard2.snippets.SnippetSettingsTest --rerun-tasks` completed with `BUILD SUCCESSFUL` after the final migration and combined-password gate fixes.

Full-suite evidence:

- Final `./gradlew testDebugUnitTest` completed with `BUILD SUCCESSFUL` after both final-review blocker fixes.

Covered contracts:

- Slot labels default to one-based empty-slot numbers or the first one/two Unicode code points of the phrase unless a custom label is set.
- Pages are grouped in seven-slot boundaries.
- `keyboard.xml` keeps `Keyboard2View` as the baseline keyboard view and inserts `SnippetRowView` as an additive sibling immediately above it.
- Taps on configured snippets route to `KeyEventHandler.snippet_entered`, preserving the normal text path and committing the exact phrase with cursor position `1` and no appended space.
- Settings include the snippets enable toggle plus a Fleksy-like fixed-slot editor with phrase entry, optional custom/icon-style label, live preview, clear, and add-page capacity.
- Raw snippet phrases persist via `SnippetStore.saveSlots(getContext(), _slots)` to `Context.getNoBackupFilesDir()/frankenkey_snippets.json`, not through default `SharedPreferences`.
- `SnippetStore.loadSlots(Context)` migrates legacy `SnippetStore.PREF_SLOTS` JSON from default preferences into the no-backup snippet file once credential-protected storage is available, then removes the backup-eligible legacy key after a successful migration/removal path.
- `DirectBootAwarePreferences.copy_shared_preferences` removes/skips legacy `SnippetStore.PREF_SLOTS`, so snippets are not copied to device-protected direct-boot preferences.
- `Keyboard2.refresh_config()` passes `_config.editor_config.should_show_snippet_row` into `SnippetRowView.refresh_config`.
- `SnippetRowView.refresh_config` clears/hides and returns before loading snippet phrases when the current editor disallows snippet text.
- `EditorConfig.should_show_snippet_row` hides snippets for text/password, visible-password, web-password, combined password-variation text inputs, and number-password fields, while preserving normal `TYPE_TEXT_FLAG_NO_SUGGESTIONS` text-field visibility.

## V003 APK build/install/IME smoke

Status: `passed`  
Recorded: `2026-07-05T19:05:46Z`

Build evidence:

- Final `./gradlew assembleDebug` completed with `BUILD SUCCESSFUL` after all blocker fixes.
- APK path: `build/outputs/apk/debug/FrankenKey-debug.apk`.
- Final `aapt2 dump badging build/outputs/apk/debug/FrankenKey-debug.apk` reported package `dev.frankenkey.keyboard.debug`, label `FrankenKey (Debug)`, launchable activity `juloo.keyboard2.LauncherActivity`, and `provides-component:'ime'`.

Runtime smoke evidence on final rebuilt APK:

- AVD `FrankenKeyParity` booted and reported `sys.boot_completed=1`.
- `adb -s emulator-5554 install -r build/outputs/apk/debug/FrankenKey-debug.apk` returned `Success`.
- `ime enable dev.frankenkey.keyboard.debug/juloo.keyboard2.Keyboard2` enabled the IME.
- `ime set dev.frankenkey.keyboard.debug/juloo.keyboard2.Keyboard2` selected the IME.
- `settings get secure default_input_method` returned `dev.frankenkey.keyboard.debug/juloo.keyboard2.Keyboard2`.
- `dumpsys input_method` contained `mCurId=dev.frankenkey.keyboard.debug/juloo.keyboard2.Keyboard2`.
- Earlier text-field smoke during this run also showed the FrankenKey `InputMethod` window owned by `dev.frankenkey.keyboard.debug` and no FrankenKey/AndroidRuntime crash lines in sampled logcat. A later Settings focus attempt was partially affected by an emulator/SystemUI ANR, not by a FrankenKey crash; the final rebuilt APK install/select smoke above completed after that.

Android Studio note:

- `/Applications/Android Studio.app` was not present; Spotlight (`mdfind "kMDItemCFBundleIdentifier == 'com.google.android.studio'"`) returned no bundle.
- Because Android Studio was not installed, visual Android Studio UI verification could not be performed. The CLI Android SDK/emulator verification above completed instead.

## R001 Recursive and adversarial signoff

Status: `passed`  
Recorded: `2026-07-05T19:05:46Z`

Review findings and resolution:

- Initial AcceptanceReviewer returned `NO BLOCKERS` before privacy hardening.
- Initial AdversarialReviewer found two privacy blockers: snippets were available in secure editor contexts, and raw phrases were stored/copied through backup/direct-boot preferences.
- After the first privacy fix, AdversarialRereviewer returned `NO BLOCKERS`.
- AcceptanceRereviewer then found an over-restriction blocker: using candidate-view policy hid snippets from normal no-suggestions text fields. Fixed with `EditorConfig.should_show_snippet_row` and `Keyboard2` wiring.
- FinalAdversarialReview then found a legacy migration blocker: existing `SnippetStore.PREF_SLOTS` values could remain in backup-eligible default preferences. Fixed by migrating legacy JSON to the no-backup file and removing the legacy key.
- FinalAcceptanceReview then found a combined-password blocker: exact-match password variation checks could miss text inputs carrying password bits with another variation. Fixed by rejecting text inputs whose variation mask carries `TYPE_TEXT_VARIATION_PASSWORD`.
- FixedAdversarialReview returned `NO BLOCKERS` after inspecting no-backup storage, legacy migration/removal, direct-boot exclusion, secure-editor hiding, and exact insertion.
- FixedAcceptanceReview returned `NO BLOCKERS` after inspecting normal no-suggestions visibility, password/number-password hiding, row wiring, no-backup storage, direct-boot exclusion, settings, insertion, and focused tests.

## Known environment notes

- Android SDK: `/Volumes/TheHoneyBadger/AndroidTooling/android-sdk`
- JDK 17: `/Volumes/TheHoneyBadger/AndroidTooling/jdks/jdk-17.0.19+10/Contents/Home`
- AVD used for smoke: `FrankenKeyParity`
- Android Studio app bundle: not installed on this workstation at verification time.
