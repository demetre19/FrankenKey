# test DOX

## Purpose

- Own FrankenKey unit/Robolectric tests and test fixtures.

## Ownership

- Tests mirror behavior in source/resources and defend user-visible contracts.
- The source root owns Gradle test execution.

## Local Contracts

- Tests must encode intent and regressions, not just current implementation shape.
- Add or update focused tests for behavior changes in clipboard, layouts, suggestions, snippets, settings, gestures, and release contracts.
- Keep tests deterministic and local; do not require live phone/RustDesk access.

## Work Guidance

- Prefer existing test classes near the affected feature.
- Use descriptive assertion messages explaining why the contract matters.

## Verification

- Run focused tests with `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew --no-daemon --no-configuration-cache testDebugUnitTest --tests <TestClass>`.

## Child DOX Index

- `juloo.keyboard2/AGENTS.md` — package-level test contracts and focused test grouping.
