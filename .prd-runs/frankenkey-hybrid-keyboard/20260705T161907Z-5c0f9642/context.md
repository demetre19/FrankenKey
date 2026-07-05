# FrankenKey Orchestration Context

Run: `20260705T161907Z-5c0f9642`  
Project slug: `frankenkey-hybrid-keyboard`  
Repo path: `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey`  
PRD: `PRD.md`  
Updated: `2026-07-05T16:57:23Z`

## Current gate

- PRD is approved by exact keyword: `APPROVE PRD`.
- Build execution is **not** approved yet.
- Do not create worktrees, spawn implementation agents, import app source, install tooling, run builds, or modify application code until the user replies exactly: `START BUILD`.

## Product decisions

- App name: **FrankenKey**.
- Platform: Android-native IME.
- Baseline: copy/preserve **Unexpected Keyboard** exactly for bottom keyboard layout and behavior.
- The only added product feature is a Fleksy-style custom snippet/hotkey row above the keyboard.
- Snippet row is visible whenever enabled.
- Snippets are shown in groups/pages of seven.
- Users scroll/swipe left and right between groups of seven.
- Tapping a snippet inserts the exact configured phrase into the active text field and keeps the keyboard open.
- No automatic trailing space.
- Snippet editor should be Fleksy-like: indexed slots, phrase field, auto-generated 1–2 character preview label, optional icon-style label, visible empty slots, edit/clear support.
- Snippet content is local-only: no cloud sync, accounts, analytics, or network access for snippet content.

## Non-negotiable implementation constraint

Unexpected Keyboard must remain the unchanged baseline. Treat snippets as an additive top-row layer. Avoid refactors, restyling, or changes to baseline key layout, gestures, modifiers, normal typing, or existing settings except where strictly required for identity/build integration and snippet row attachment.

## Fleksy reference evidence

- APK reference: `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/Fleksy/fleksy-keyboard-11-0-0.apk`.
- Decompiled behavior found `HotKeysActivity`, `HotKeyEditActivity`, `HotKey(index, icon, phrase)`, and `HotKeyExtensionBar`.
- Fleksy stored hotkeys locally in `hotkey_prefs` keyed by index with JSON `[isFont, icon, phrase]`.
- Fleksy showed seven hotkey buttons per visible group/extension.
- Fleksy editor auto-generated labels from phrase text unless icon override was chosen.
- Do not copy Fleksy source; use behavior only as reference.

## Upstream Unexpected Keyboard grounding

- Repo: `https://github.com/Julow/Unexpected-Keyboard`.
- Language/build: Java Android app, Android Gradle Plugin `8.13.2`, namespace/application id `juloo.keyboard2`, minSdk `21`, target/compile SDK `36`, GPLv3.
- Manifest entry point: `juloo.keyboard2.Keyboard2` service with `android.permission.BIND_INPUT_METHOD` and `android.view.InputMethod` action.
- Settings entry: `juloo.keyboard2.SettingsActivity` using `PreferenceActivity` and `res/xml/settings.xml`.
- Input view flow:
  - `Keyboard2.onCreate()` initializes config, suggestions, `KeyEventHandler`, then calls `create_keyboard_view()`.
  - `create_keyboard_view()` inflates `R.layout.keyboard` and finds `R.id.keyboard_view` and `R.id.candidates_view`.
  - `res/layout/keyboard.xml` is a vertical `LinearLayout` containing `CandidatesView` then `Keyboard2View`.
  - `Keyboard2.onStartInputView()` refreshes config/layout, starts handler, and calls `setInputView(_keyboard_container_view)`.
  - `Keyboard2View` owns touch hit-testing and delegates key presses to `_config.handler`.
  - `KeyEventHandler.send_text(String text)` calls `InputConnection.commitText(text, 1)`.
- Android docs confirm IMEs extend `InputMethodService` and commit text through `InputConnection.commitText(CharSequence, int)`.

## Planning artifacts

- `dependency-tree.csv`: DAG and proposed lanes.
- `todo.csv`: executable implementation/verification checklist.
- `agent-roster.csv`: planned specialist roles; no agents launched yet.
- `access-requirements.md`: no credential blockers; environment prerequisites recorded.
- `debug-deep.md`: Level 0/1 grounding and flow diagrams.
- `proof-of-concept.md`: proof gates P001-P005.
- `completion-report.md`: evidence placeholders.

## Proposed worktree lanes after `START BUILD`

No worktrees exist yet. Proposed paths:

- Foundation: `.prd-runs/frankenkey-hybrid-keyboard/20260705T161907Z-5c0f9642/worktrees/foundation`
- Snippet model: `.prd-runs/frankenkey-hybrid-keyboard/20260705T161907Z-5c0f9642/worktrees/snippets-model`
- Runtime row: `.prd-runs/frankenkey-hybrid-keyboard/20260705T161907Z-5c0f9642/worktrees/runtime-row`
- Settings editor: `.prd-runs/frankenkey-hybrid-keyboard/20260705T161907Z-5c0f9642/worktrees/settings-editor`
- Verification: `.prd-runs/frankenkey-hybrid-keyboard/20260705T161907Z-5c0f9642/worktrees/verification`
- Review: `.prd-runs/frankenkey-hybrid-keyboard/20260705T161907Z-5c0f9642/worktrees/review`

## Access and environment notes

- No secrets/accounts are required for the approved PRD.
- User explicitly approved installing Android Studio, Android SDK, or related tooling as needed, but installs must go on external drive `/Volumes/TheHoneyBadger/`.
- Observed local tooling: Git, Java, keytool, Python, and adb are present.
- Observed missing/ambiguous tooling: no Android SDK path detected, no Android Studio at default path, no FontForge on PATH.
- Observed verification gap: `adb devices` currently lists no attached device/emulator.
- After `START BUILD`, first task should resolve Android SDK/tooling under `/Volumes/TheHoneyBadger/` and decide whether FontForge is required for the upstream build.

## Restart protocol

On resume:

1. Read `run-state.json`, `dependency-tree.csv`, `todo.csv`, `agent-roster.csv`, `context.md`, `events.jsonl`, `debug-deep.md`, and `proof-of-concept.md`.
2. Rebuild session todos from `todo.csv`.
3. If `START BUILD` has not been received, remain in planning gate.
4. If `START BUILD` has been received, create worktrees only for ready tasks and follow the proof gates before implementation.
5. Treat SQLite/events/files as source of truth, not memory.
