# juloo.keyboard2 tests DOX

## Purpose

- Own package-level behavior/regression tests.

## Ownership

- This folder owns tests mirroring app package domains.

## Local Contracts

- Explain user-visible/architectural intent in assertions.
- Prefer focused existing classes and Robolectric-friendly fakes.
- Cover boundaries: user data, permissions, visibility, gestures, ranking, and state transitions.
- Resource Robolectric tests use `@Config(sdk = 35)` and must appear in generated result XML; target API 36 exceeds the pinned runner's API 35 ceiling.
- Keep live-device verification separate.

## Work Guidance

- Run only affected classes unless failure requires expansion; adaptive-learning changes cover personalization, scoring, decoder, key events, candidates, Settings confirmation/theme, and Direct Boot privacy.

## Verification

- Use the parent focused Gradle command and confirm generated test-result XML.

## Child DOX Index

- None.
