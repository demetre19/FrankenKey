# suggestions DOX

## Purpose

- Own the shared suggestion/autocorrect decoder, asynchronous request controller, personalization ranking, touch geometry, and candidate presentation.

## Ownership

- `Decoder` is the sole synchronous scoring policy; `SharedDecoder` owns worker serialization, resource lifetimes, stale-result rejection, and immutable presentations.
- Keyboard event routing and IME session/layout/resource integration live in the parent package.

## Local Contracts

- Suggestions and separator-time autocorrect must derive from the same immutable request/result; no second scoring path or synchronous commit-time decode is allowed.
- Native candidate recall is bounded, Unicode-scalar aware, coordinate-first, and scored against the exact visible keyboard layout.
- Only an exact current `RequestKey` may publish, prepare a commit token, learn/unlearn, or receive a candidate action; PENDING/EMPTY states are non-clickable. A prepared token may commit after the typed-word key advances only while its captured session and personalization domain remain valid.
- The IME passively prewarms the exact dictionary, Hunspell, and personalization descriptors before the first editor; same-key resources remain worker-resident across editor sessions, while active resource changes advance through explicit epoch updates.
- Learned words must remain reversible, and ranking must combine dictionary, geometry, frequency, unigram, bigram, and bounded typo-to-target evidence deterministically without user-visible debug labels.
- Candidate UI stays compact: at most three single-line word candidates plus the separate emoji slot unless intentionally redesigned.
- Accepted typo corrections are stored only in credential-protected preferences. Correction-only targets survive restart without becoming unigrams or next-word anchors, and learning remains active when either Suggestions or Autocorrect is enabled.
- Exact correction identity is NFC-normalized lowercase editor text, distinct from accent-folded dictionary canonical keys; accepted exact pairs may contain at most two textual optimal-string-alignment edits and never consult key geometry.
- Exact evidence is recalled ahead of related-only evidence, improves ranking from the first acceptance, and after four accepted exact corrections may override a protected literal or nearer ordinary guess. Related same-index adjacent-key evidence is weaker and never unlocks that override.
- Changed candidate/autocorrect replacements keep their prepared learning token pending through the immediate-Backspace window. Exact undo rejects the changed target but commits any prior editor-verified manual-correction fallback; the next accepted action commits the changed token once.
- Forgetting a learned word removes correction evidence where it is either source or target, and clearing adaptive learning must also clear any inactive prewarmed worker state.

## Work Guidance

- Keep scoring constants, bounds, source merging, confidence margins, and tie-breaking explicit and deterministic.
- Keep Cdict, Hunspell, and personalization access worker-confined through `SharedDecoder`; the main thread must never wait for decoding.

## Verification

- Run `SuggestionPersonalizationTest`, `CdictSpatialQueryTest`, `SharedDecoderTest`, `CandidatesViewPresentationTest`, `AutocorrectScoringTest`, and focused key-event learning/autocorrect tests when relevant.

## Child DOX Index

- No child AGENTS.md files currently. Suggestion classes are owned here.
