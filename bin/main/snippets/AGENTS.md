# snippets DOX

## Purpose

- Own snippet storage/settings, row/page UI, and insertion helpers.

## Ownership

- This folder owns snippet classes; parent owns key routing and Settings integration.

## Local Contracts

- Preserve user snippets across applicable storage contexts; never leak phrases into inappropriate protected preferences.
- Both keyboard modes support snippets.
- Persist stable Lucide IDs (not drawable IDs); picker stays 56 unique packaged icons in eight groups of seven.
- Icon buttons use theme label tint with centered 20dp art. Read-but-ignore legacy `iconLabel`; accessibility labels name icons, never potentially secret phrases.
- Short bounded horizontal swipes change pages circularly (right=forward, left=back); sub-threshold taps and vertical gestures remain unaffected.

## Work Guidance

- Keep migrations explicit/tested and reuse row/page patterns.

## Verification

- Run focused snippet storage, seam, and layout tests.

## Child DOX Index

- None.
