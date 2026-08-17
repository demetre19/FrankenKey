# autocorrect DOX

## Purpose

- Own the shared decoder worker's Hunspell JNI bridge.

## Ownership

- This folder owns Hunspell load/spell/suggest/close; `../suggestions/Decoder.java` owns scoring and the parent owns separator commits.

## Local Contracts

- Serialize all load/use/close work on `SharedDecoder`; never call from UI/main thread.
- `close()` is the sole native lifetime boundary: no finalizers or second scorer.
- Fail conservatively when packs cannot load.

## Work Guidance

- Keep confidence, case, and word-protection policy in the shared decoder.

## Verification

- Run `AutocorrectScoringTest`, `SharedDecoderTest`, and focused key-event autocorrect/undo tests.

## Child DOX Index

- None.
