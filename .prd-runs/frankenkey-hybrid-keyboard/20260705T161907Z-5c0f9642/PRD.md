# PRD: FrankenKey Hybrid Keyboard

Run: `20260705T161907Z-5c0f9642`  
Mode: `NEW_BUILD`  
Status: Awaiting explicit PRD approval (`APPROVE PRD`)

## 1. Product Summary

Build **FrankenKey**, an Android-native keyboard app that copies/preserves **Unexpected Keyboard** exactly for the bottom keyboard layout and behavior, with only a Fleksy-inspired custom snippet/hotkey row added above it.

The product goal is not to clone Fleksy’s full extension store or SDK ecosystem. The required Fleksy-inspired behavior is specifically: users can define custom snippets and launch them from buttons in a top row while typing.

## 2. Goals

1. Copy/preserve the underlying Unexpected Keyboard typing experience exactly as the baseline keyboard.
2. Add a top snippet row above the keyboard that is visible whenever enabled.
3. Let users create, edit, and clear custom snippet slots in a Fleksy-like editor.
4. Show snippet buttons in groups of seven.
5. Allow horizontal left/right scrolling or swiping between additional groups of seven.
6. Insert the configured snippet phrase into the active text field when a snippet button is tapped.
7. Keep the keyboard open after inserting a snippet.
8. Store snippets locally only, with no cloud sync, accounts, analytics, or network access for snippet content.
9. Ship under a separate FrankenKey identity, not as a hidden modification of Unexpected Keyboard.

## 3. Non-Goals

1. Do not clone Fleksy’s full extension marketplace/store.
2. Do not depend on the Fleksy SDK for the snippet row unless later implementation work proves it is strictly necessary.
3. Do not alter Unexpected Keyboard’s core key layout, normal typing behavior, gestures, modifiers, or existing settings; snippet functionality must be an additive layer above it.
4. Do not add snippet cloud sync, accounts, remote backup, or networked snippet storage.
5. Do not add unrelated keyboard features while implementing the snippet row.
6. Do not build iOS support for this scope.

## 4. Evidence and Source Behavior

### 4.1 Fleksy SDK repository research

The provided Fleksy SDK repository advertises support for custom views on top of the keyboard, but the publicly inspected sample code did not expose the old Fleksy app Hotkey Extension UI source. The PRD therefore treats Fleksy behavior as a reference to recreate natively on top of Unexpected Keyboard.

### 4.2 Fleksy APK evidence

The provided APK at `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/Fleksy/fleksy-keyboard-11-0-0.apk` was decompiled for behavioral reference. Relevant findings:

- Fleksy included `HotKeysActivity` and `HotKeyEditActivity`.
- Fleksy stored hotkeys locally in `hotkey_prefs`, keyed by index, with JSON shaped like `[isFont, icon, phrase]`.
- Fleksy represented runtime hotkeys as `HotKey(index, icon, phrase)` and supplied them to `HotKeyExtensionBar` through `ExtensionBar.HotKeys`.
- Fleksy showed seven hotkey buttons per visible hotkey extension group.
- Fleksy’s edit UI auto-generated a 1–2 character preview label from the typed phrase unless the user chose an icon-style label.
- Fleksy saved a hotkey only when both icon/label and phrase were non-empty.

These findings guide UX and data behavior; the implementation should be original code in FrankenKey.

## 5. User Experience Requirements

### 5.1 Keyboard baseline

- FrankenKey is an Android input method editor (IME).
- The bottom keyboard must copy/preserve Unexpected Keyboard exactly as the functional and layout baseline.
- Normal typing, modifiers, gestures, existing layout behavior, and settings inherited from Unexpected Keyboard must remain intact; snippet functionality is only a Fleksy-style add-on layer above the keyboard.

### 5.2 Snippet row visibility

- The snippet row appears above the keyboard whenever the feature is enabled.
- The row can be disabled from app settings.
- When enabled, the row should remain present while the keyboard is open, even if some slots are empty.

### 5.3 Snippet grouping and navigation

- Snippet buttons are organized into groups of seven.
- Exactly seven snippet positions are visible per group/page where screen width allows.
- Users can scroll or swipe left and right to reveal additional groups of seven.
- The interaction should feel like a horizontally paged row of hotkey groups, not a single dense unstructured list.
- Empty slots should be visually available/configurable in the editor; runtime display may show empty/add affordances only if that matches the inherited keyboard design cleanly.

### 5.4 Snippet button display

Each snippet slot has:

- an index/order,
- a phrase to insert,
- a visible label/icon string,
- an optional flag indicating whether the label is an icon-style label.

Default label behavior:

- If the phrase is empty, use the slot number or empty-slot affordance.
- If the phrase has one character, default label is the first character.
- If the phrase has two or more characters, default label is the first two characters.
- Users can override the generated label with an icon-style label in the Fleksy-like editor.

### 5.5 Snippet insertion

- Tapping a configured snippet button commits that slot’s phrase into the active app text field.
- The keyboard remains open after insertion.
- FrankenKey must not automatically append a trailing space.
- Snippets should work for emails, URLs, emoji, punctuation, short phrases, and longer text snippets.

### 5.6 Snippet management UI

Provide a Fleksy-like editor in FrankenKey settings:

- fixed indexed slots,
- phrase entry field,
- auto-generated preview label from the phrase,
- optional icon-style label selection,
- visible empty slots,
- ability to add/edit/clear snippet slots.

Reordering is acceptable if it falls naturally out of the slot model, but it is not required for the first approved build unless needed to manage groups cleanly.

## 6. Data and Privacy Requirements

- Snippet content is local-only.
- No accounts.
- No cloud sync.
- No analytics collection for snippet content.
- No network access is required for snippet storage or runtime insertion.
- Storage should be simple and maintainable; a local preferences or small local database model is acceptable.
- Stored data should preserve slot order, phrase, label/icon, and icon-style flag.

## 7. Android App Identity

- App name: **FrankenKey**.
- Package id: use a new separate package id such as `dev.frankenkey.keyboard` unless implementation constraints require a different reverse-DNS id.
- FrankenKey should not present itself as the original Unexpected Keyboard app.

## 8. Technical Direction

1. Use Unexpected Keyboard as the Android keyboard foundation and preserve its keyboard behavior exactly.
2. Add a native snippet-row layer above the existing keyboard view without changing the baseline keyboard interaction model.
3. Implement grouped horizontal navigation for snippet buttons in groups of seven.
4. Implement snippet persistence locally.
5. Implement settings/editor screens integrated into the Android app.
6. Keep Fleksy APK behavior as a reference, not copied source.
7. Keep implementation minimal and direct; avoid speculative plugin systems or a full extension architecture.

## 9. Acceptance Criteria

### 9.1 Build and install

- The Android project builds successfully into a debug APK.
- The app installs as a distinct FrankenKey keyboard package.
- Android can enable/select FrankenKey as an input method.

### 9.2 Baseline keyboard

- The inherited Unexpected Keyboard layout is available for typing and remains the unchanged baseline.
- Normal key entry, modifiers, gestures, and settings still behave like Unexpected Keyboard.
- The snippet row is additive and does not intentionally change baseline keyboard behavior.

### 9.3 Snippet row

- When the snippet feature is enabled, a row appears above the keyboard.
- The row shows seven snippet positions per visible group.
- Additional snippet groups can be reached by horizontal left/right scrolling or swiping.
- The row remains available while typing.

### 9.4 Snippet editor

- Users can open a FrankenKey settings/editor screen for snippets.
- Users can create or edit a snippet phrase.
- The editor generates a preview label from the phrase using the first one or two characters.
- Users can optionally choose an icon-style label.
- Users can clear a snippet slot.
- Empty slots are represented clearly.

### 9.5 Insertion behavior

- Tapping a configured snippet button inserts the exact configured phrase into the active text field.
- The keyboard stays open after insertion.
- No trailing space is added unless it is part of the configured phrase.
- Email/URL-style snippets insert without corruption.

### 9.6 Local-only privacy

- Snippet data persists across app restarts on the same device.
- Snippet content is not sent to a network service.
- The app does not require account login for snippets.

## 10. Verification Plan

After implementation starts, verify with targeted checks only:

1. Build the debug APK.
2. Run unit or instrumentation tests for snippet data persistence and label generation where feasible.
3. Smoke-test the IME path: enable/select FrankenKey, type normal keys, tap snippet buttons, confirm inserted text.
4. Smoke-test group navigation: configure more than seven snippets and confirm left/right navigation exposes the next group.
5. Smoke-test privacy expectations by checking there is no snippet sync/account/network path introduced for snippet content.

## 11. Approval Gate

Implementation must not start until this PRD is explicitly approved.

To approve this PRD and unlock build planning, reply exactly:

`APPROVE PRD`
