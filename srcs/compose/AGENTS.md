# compose DOX

## Purpose

- Own compose/dead-key, shift, fn, and numpad JSON inputs.

## Ownership

- `compose/` owns inputs; output subtrees own generated artifacts.

## Local Contracts

- Keep JSON valid, deterministic, locale-aware, and consistent with existing key names.

## Work Guidance

- Run generators; never hand-edit generated output. Keep additions minimal and locally sorted/grouped.

## Verification

- Run the relevant generation/check path and focused compose/key tests when covered.

## Child DOX Index

- None.
