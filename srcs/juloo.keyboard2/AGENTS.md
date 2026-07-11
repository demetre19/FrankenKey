# juloo.keyboard2 DOX

## Purpose

- Own FrankenKey Java app logic: IME service, keyboard view, input handling, clipboard, snippets, settings, GIF/emoji, layouts, themes, and app integration.

## Ownership

- This package owns runtime behavior for the Android keyboard.
- Child packages own focused domains listed below.

## Local Contracts

- Preserve the clean/Fleksy default path and optional dense FrankenKey path.
- Clipboard history is capped at 50 and user-data handling must remain local/private.
- Backspace, gestures, snippets, suggestions, and settings are user-visible contracts; update focused tests for changes.
- Avoid compatibility shims; migrate callsites cleanly.
- Suggestions and autocorrect use one session-scoped asynchronous decoder; visible layout geometry, resources, privacy gates, and request identity must be updated atomically through the IME lifecycle.
- Paste and destructive delete holds use independent repeat intervals; after a hold emits at least one repeat, release must not emit an extra paste or delete action.
- The official Termux `TYPE_NULL` editor uses raw `KEYCODE_DEL` events for Backspace and locally tracked raw replacement for suggestions/autocorrect. Keep this path stateless: do not read, display, or write persistent personalization in terminal input, which may contain hidden passwords.
- Autocapitalisation uses delayed, editor-verified suffix repair for sentence/word starts. Normal, long-message, and short-message text fields missing caps flags fall back to sentence mode; URI, web, email, and password fields do not. Exact standalone lowercase `i` is promoted only at a verified word boundary in non-password text fields. Every surrounding-text correction or undo must synchronise autocap with the actual UTF-16 removal length and inserted text.
- Adaptive learning records editor-verified typo corrections through prepare-before-mutation commit tokens whenever Suggestions or Autocorrect is active; repeated exact pairs may cover up to two textual edits. Immediate verified correction undo rejects the changed target; if that change followed a manual correction, undo preserves the prior manual evidence instead of losing both.
- Destructive learning controls require explicit confirmation: Settings clear-all uses an activity dialog, and live candidate/keyboard word-unlearn actions use an IME-attached dialog whose delayed positive action revalidates the exact request.
- `SettingsActivity` uses the dark platform Material theme by default independently of the keyboard theme preference, which remains system-controlled unless explicitly changed.
- Release update checks run only from unlocked launcher/settings activities, never the IME. They default to daily checks, show the GitHub Release changelog, require explicit accept/reject, authenticate the APK before installer handoff, and never clear or migrate keyboard user data.

## Work Guidance

- Use LSP/code navigation for exported symbols when available.
- Prefer small feature-local changes over cross-package refactors.
- Keep Android API guards explicit for version-specific behavior.

## Verification

- Run focused tests for touched behavior under `test/juloo.keyboard2`.
- For release-visible behavior, build release and copy the APK to the delivery root before asking for device testing.
- Updater logic changes must satisfy the source-root version-to-version device verification.

## Child DOX Index

- `suggestions/AGENTS.md` — shared decoder, spatial scoring, personalization, stale-result control, and candidate presentation.
- `autocorrect/AGENTS.md` — worker-confined Hunspell JNI bridge and explicit native lifetime.
- `snippets/AGENTS.md` — snippet storage, UI rows, insertion, settings.
- `prefs/AGENTS.md` — custom preference widgets and settings controls.
- `dict/AGENTS.md` — dictionary list/loading UI and supported dictionaries.
- `lang/AGENTS.md` — language pack model and manager.
