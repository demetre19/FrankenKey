# layouts DOX

## Purpose

- Own source keyboard-layout XML and licenses.

## Ownership

- Edit source here; generated/package XML belongs to `res/xml`.

## Local Contracts

- Keep clean mode free of default Ctrl/arrow clutter and preserve hidden edit gestures.
- Clean/dense text layouts reserve G alone for hidden four-direction cursor sliders so other letter swipes remain available for teach/forget; preserve taps/diagonals.
- Keys duplicated by the Extra Keys panel (Esc, Tab, arrows, Home/End/PgUp/PgDn, Ins, Del, F-keys, Ctrl, Alt, Meta) use `hide` corner gestures — invisible, always active for SSH — never `loc`, since they are no longer in the extra-key catalog and `loc` would strip them.
- Preserve upstream layout attribution/licenses.

## Work Guidance

- Change source, not generated output; check bottom rows and side labels against tests.

## Verification

- Run the layout generator/check and relevant focused tests (`CleanModeFleksyLayoutTest`, `ClipboardParityTest`).

## Child DOX Index

- None.
