# snippets DOX

## Purpose

- Own snippet storage, snippet settings UI, row rendering, page model, and insertion helpers.

## Ownership

- This folder owns snippet-domain Java classes.
- Keyboard key routing and settings activity integration live in the parent package.

## Local Contracts

- Snippets are user-authored data; preserve them across relevant storage contexts.
- Direct-boot behavior must not leak raw snippet phrases into inappropriate protected preferences.
- Everyday and FrankenKey modes share snippet capability.
- Snippet buttons may store a stable Lucide icon ID or use their text-label/phrase fallback. Keep the curated picker at 56 unique, locally packaged icons in eight seven-icon groups; never serialize build-specific drawable IDs.
- Legacy serialized `iconLabel` fields remain readable but ignored. Icon accessibility labels must describe the icon without speaking the saved phrase, which may contain passwords or other secrets.
- Snippet-row icons and text labels must share the active FrankenKey `Theme` foreground, corrected to at least 4.5:1 contrast against `colorKeyboard` for built-in and custom light/dark themes.

## Work Guidance

- Keep storage migrations explicit and tested.
- Use existing row/page patterns for UI changes.

## Verification

- Run focused snippet tests such as `SnippetStoreTest` or seam/layout tests when changed.

## Child DOX Index

- No child AGENTS.md files currently. Snippet classes are owned here.
