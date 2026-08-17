# suggestions DOX

## Purpose

- Own shared decoding, request control, ranking/personalization, touch geometry, and candidate presentation.

## Ownership

- `Decoder` owns synchronous scoring; `SharedDecoder` owns worker serialization/resources/stale rejection/presentations; parent owns IME routing/session integration.

## Local Contracts

- Suggestions and separator autocorrect share one immutable request; no main-thread/commit-time decode. Preview is bounded; separators escalate the exact request to full nearby/Hunspell work.
- Three-code-point early correction requires complete evidence, same-length one-substitution Cdict winner, explicit frequency/literal/runner-up margins, and equality with exhaustive output.
- Pending boundaries commit literal separators immediately. Late correction requires identical request/session/connection/cursor/source; unrelated or out-of-window mutation freezes literal text.
- Retain a bounded worker result queue. Parent may replace ≤48 preceding unchanged absolute ranges within ≤3 following sentences/768 UTF-16 units; unreadable/raw editors are excluded. Only the current request publishes visibly.
- Preserve touch traces only for identical normalized word/cursor/code-point count with complete readback.
- Native recall is bounded, Unicode-scalar/layout-coordinate aware, and completes nearby/Hunspell passes for unknown words. Resource/corruption failures fail closed.
- Truncation alone does not veto a clear recognized one-edit winner; two-edit repairs require complete evidence. Apply explicit omission/deletion ambiguity gates for short words and repeated letters.
- Only exact current `RequestKey` may publish, commit, learn/forget, or accept actions; PENDING/EMPTY are inert. Prepared tokens require valid captured session/domain.
- Prewarm exact resource descriptors; same-key worker resources survive sessions and changes advance by epochs.
- Treat matching Cdict or bundled Hunspell as installed; never show a false install banner.
- Ranking deterministically combines dictionary, geometry, frequency, unigram, bounded bigram/trigram, and typo evidence; no debug labels. Retain up to six ranked single-line words: show ranks 1–3 first, let a left swipe bring ranks 4–6 in from the right, and let a right swipe return; keep the emoji slot separate.
- Credential-protected vocabulary stays reversible. Explicit Teach persists exact casing only for 2–3-letter tokens and may use the credential store from a safe prose session whose host disabled passive personalization, without enabling passive reads or writes for that session. Correction-backed Adaptive targets require decoder-validated backspace/edit plus suggestion selection. Ordinary commits and automatic autocorrection may update runtime context/safe touch calibration only; they never persist unigram, bigram, or correction evidence. A one-time policy migration preserves Taught words and historical correction targets, removes passive-only words/bigrams, and preserves touch calibration.
- Touch calibration is worker-confined, bounded, complete-evidence-only, active after minimum samples, subtracted from coordinates, and cleared with personalization; corrected-source touches never train.
- Correction IDs use NFC lowercase exact editor text, not accent-folded keys. Learned correction evidence is an exact previous/source/target triple; packaged locale priors may use one or two preceding words from corpus-derived high-confidence sequences, but code must not hardcode phrases or synthesize cross-products.
- Exact evidence outranks related evidence from first acceptance; four accepted exact corrections may override protected literals. Related adjacent-key evidence never unlocks override.
- Protect cold short all-caps tokens, valid/learned/technical/proper-name literals, ambiguous short repairs, and lowercase requests from improper casing.
- Narrow exceptions remain explicit: lowercase `j`→`I`; a unique nearby frequent Cdict substitution for unlearned lowercase 2–3-letter tokens absent from Cdict; decisive context repair; supported 3-letter adjacent transposition; validated contraction/apostrophe repairs; exact learned policy for `im`→`I'm`; validated deterministic English inflections.
- Changed replacements retain learning tokens only until the next accepted action. Backspace/cursor movement settle replacements and never restore source.
- Forgetting removes evidence where the word is source/target; clear-all also clears inactive prewarmed state.

## Work Guidance

- Keep bounds, constants, margins, merging, and ties explicit/deterministic; all Cdict/Hunspell/personalization access, including deliberate Teach writes from stateless sessions, stays on the `SharedDecoder` worker.

## Verification

- Run `SuggestionPersonalizationTest`, `CdictSpatialQueryTest`, `SharedDecoderTest`, `CandidatesViewPresentationTest`, `AutocorrectScoringTest`, and relevant key-event tests.

## Child DOX Index

- None.
