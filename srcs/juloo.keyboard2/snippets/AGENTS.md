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
- Snippet labels render as ordinary text; legacy serialized `iconLabel` fields remain readable but are ignored and are not written by current storage.

## Work Guidance

- Keep storage migrations explicit and tested.
- Use existing row/page patterns for UI changes.

## Verification

- Run focused snippet tests such as `SnippetStoreTest` or seam/layout tests when changed.

## Child DOX Index

- No child AGENTS.md files currently. Snippet classes are owned here.
