# juloo.keyboard2 tests DOX

## Purpose

- Own package-level focused tests for FrankenKey keyboard behavior.

## Ownership

- This folder owns tests for app package behavior and regression contracts.
- Subfolders mirror source subdomains when present.

## Local Contracts

- Tests must explain user-visible or architectural intent in assertion messages.
- Prefer adding coverage to an existing focused test class over creating broad catch-all tests.
- Keep live device/RustDesk verification separate from unit tests.

## Work Guidance

- Use Robolectric-friendly fakes and existing helper patterns.
- Cover edge cases for user data, permission state, layout visibility, and gesture/ranking branches.
- Resource-backed `SettingsActivity` Robolectric tests depend on Gradle unit-test Android resources; confirm their class appears in generated test-result XML rather than trusting only the aggregate task status.

## Verification

- Run only the touched/focused test classes with Gradle unless a broader failure demands more. Adaptive-learning changes require the focused personalization, scoring, shared-decoder, key-event, candidate, Settings dialog/theme, and Direct Boot privacy contracts.

## Child DOX Index

- No child AGENTS.md files currently. Test subfolders are owned here.
