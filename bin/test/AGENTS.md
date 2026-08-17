# test DOX

## Purpose

- Own unit/Robolectric tests and fixtures.

## Ownership

- Tests mirror source contracts; source root owns Gradle execution.

## Local Contracts

- Defend user-visible intent, not implementation shape.
- Keep tests deterministic/local; device checks stay separate.
- Extend focused existing classes for behavior changes.

## Work Guidance

- Use descriptive failure messages.

## Verification

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew --no-daemon --no-configuration-cache testDebugUnitTest --tests <TestClass>`

## Child DOX Index

- `juloo.keyboard2/AGENTS.md` — package test contracts.
