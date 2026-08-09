# layouts DOX

## Purpose

- Own source keyboard-layout XML and licenses.

## Ownership

- Edit source here; generated/package XML belongs to `res/xml`.

## Local Contracts

- Keep clean mode free of default Ctrl/arrow clutter and preserve hidden edit gestures.
- Clean/dense text layouts reserve G alone for hidden four-direction cursor sliders so other letter swipes remain available for teach/forget; preserve taps/diagonals.
- Preserve upstream layout attribution/licenses.

## Work Guidance

- Change source, not generated output; check bottom rows and side labels against tests.

## Verification

- Run the layout generator/check and relevant focused tests (`CleanModeFleksyLayoutTest`, `ClipboardParityTest`).

## Child DOX Index

- None.
