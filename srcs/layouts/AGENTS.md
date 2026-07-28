# layouts DOX

## Purpose

- Own source keyboard layout XML files used to generate packaged layout resources.

## Ownership

- This folder owns source layout definitions and layout license material.
- Packaged/generated XML under `res/xml` is owned by `res/` after generation.

## Local Contracts

- Keep everyday clean layout free of default Ctrl/arrow clutter.
- Preserve hidden gesture/edit shortcuts unless intentionally changing keyboard behavior.
- Dense text-layout middle-row keys expose hidden west/east/north/south cursor sliders as the same touchpad origin as clean text mode; keep taps and existing diagonal actions intact.
- Maintain upstream layout attribution/license files.

## Work Guidance

- Prefer editing the source layout here when a generated resource derives from it.
- Verify bottom rows and side labels against existing layout tests.

## Verification

- Run layout generator/check script when layout sources change.
- Run focused layout tests such as `CleanModeFleksyLayoutTest` or `ClipboardParityTest` when relevant.

## Child DOX Index

- No child AGENTS.md files currently. Layout source files are owned here.
