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
- Short bounded horizontal swipes change pages circularly in the visible drag direction (left=next page, right=previous page); sub-threshold taps and vertical gestures remain unaffected. First↔last wraps slide smoothly: at a scroll edge the far-end page is rotated next to the visible edge when the gesture begins, so the wrapped page is a real scroll neighbour; two-page strips fall back to an instant wrap.

## Work Guidance

- Keep migrations explicit/tested and reuse row/page patterns.

## Verification

- Run focused snippet storage, seam, and layout tests.

## Child DOX Index

- None.
