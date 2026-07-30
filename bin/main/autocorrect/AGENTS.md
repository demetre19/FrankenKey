# autocorrect DOX

## Purpose

- Own the Hunspell JNI bridge used by the shared decoder worker.

## Ownership

- This folder owns Hunspell construction, spelling, suggestion, and explicit-close Java/JNI code.
- Autocorrect scoring/policy lives in `../suggestions/Decoder.java`; separator commit behavior lives in the parent package.

## Local Contracts

- Hunspell load/use/close must remain serialized on `SharedDecoder`'s worker; never expose or call it from UI/main-thread code.
- `close()` is the sole native lifetime boundary; do not restore finalizer-based cleanup or a second autocorrect scorer.

## Work Guidance

- Keep the bridge narrow and fail conservatively when a language pack cannot load.
- Autocorrect confidence, case preservation, and short/known-word protection belong to the shared decoder policy.

## Verification

- Run `AutocorrectScoringTest`, `SharedDecoderTest`, and focused key-event autocorrect/undo tests for changes here.

## Child DOX Index

- No child AGENTS.md files currently. Autocorrect classes are owned here.
