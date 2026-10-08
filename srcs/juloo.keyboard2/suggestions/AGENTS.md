# suggestions DOX

## Purpose

- Own shared decoding, request control, ranking/personalization, touch geometry, and candidate presentation.

## Ownership

- `Decoder` owns synchronous scoring; `SharedDecoder` owns worker serialization/resources/stale rejection/presentations; `CandidateRole`, `CandidateSource`, `EditorContext` and `SuggestionAcceptor` are the cross-lane seams; parent owns IME routing/session integration.

## Local Contracts

- Suggestions and separator autocorrect share one immutable request; no main-thread/commit-time decode. Preview is bounded; separators escalate the exact request to full nearby/Hunspell work.
- Three-code-point early correction requires complete evidence, same-length one-substitution Cdict winner, explicit frequency/literal/runner-up margins, and equality with exhaustive output.
- Pending boundaries commit literal separators immediately. Late correction requires identical request/session/connection/cursor/source; unrelated or out-of-window mutation freezes literal text.
- Retain a bounded worker result queue. Parent may replace ≤48 preceding unchanged absolute ranges within ≤3 following sentences/768 UTF-16 units; unreadable/raw editors are excluded. Only the current request publishes visibly.
- Preserve touch traces only for identical normalized word/cursor/code-point count with complete readback.
- Native recall is bounded, Unicode-scalar/layout-coordinate aware, and completes nearby/Hunspell passes for unknown words. Resource/corruption failures fail closed.
- Truncation alone does not veto a clear recognized one-edit winner; two-edit repairs require complete evidence. Apply explicit omission/deletion ambiguity gates for short words and repeated letters.
- `CandidateRole` is the strip's public role enum. Roles with no display or accept handler render and read as plain words; no debug or source labels are shown.
- `SharedDecoder.register_source` adds worker-side `CandidateSource`s whose pinned candidates merge ahead of ranked words in READY, NFC-deduplicated, capped at six entries with the ranked remainder. Sources answer in ≤2 ms with no post-warm-up I/O.
- `DomainSuggestions` is the email-domain `CandidateSource`: in EMAIL-class editors only, text ending `@` + a valid partial domain suffix pins the static ranked domain list (bare `@` pins the top list). Its `EMAIL_DOMAIN` candidates replace the whole `@…` span with `local@domain ` in one commit via `KeyEventHandler.domain_suggestion_entered`; they never touch the learned-words store.
- `EditorContext` snapshots editor class, personalized-learning flag, bounded surrounding text, and locale on the main thread; each request carries the context captured when it was made.
- `CandidatesView` sends every tap, swipe-accept and learn gesture through `IKeyEventHandler.candidate_accepted(ticket, role, text)`, dispatched by `SuggestionAcceptor` role registrations; `candidate_long_pressed(ticket, role, text)` fires at the platform long-press without consuming the tap. Unregistered roles are dropped.
- `KeyEventHandler.BackspaceHook` (`isRepeat` flag) and `Pointers.SpaceGestureHook` (raw 16-way direction, pre-snap) are ordered hook lists that default empty.
- Only exact current `RequestKey` may publish, commit, learn/forget, or accept actions; PENDING/EMPTY are inert. Prepared tokens require valid captured session/domain.
- Prewarm exact resource descriptors; same-key worker resources survive sessions and changes advance by epochs.
- Treat matching Cdict or bundled Hunspell as installed; never show a false install banner.
- Ranking deterministically combines dictionary, geometry, frequency, unigram, bounded bigram/trigram, and typo evidence; no debug labels. Retain up to six ranked single-line words: show ranks 1–3 first, let a left swipe bring ranks 4–6 in from the right, and let a right swipe return; keep the emoji slot separate.
- Credential-protected vocabulary stays reversible. Explicit Teach persists only after positive confirmation, including safe prose sessions whose host disabled passive personalization. Unknown-literal repetition uses a bounded session-only counter and requests review on the third exact commit without persisting passive observations. A user choice may keep the word, install a fixed exact replacement, or permanently delegate to the best safe recognized/learned candidate until that rule is edited/deleted. Fixed exact replacements reserve their candidate before every bounded dictionary, Hunspell, adaptive, and personal provider and the final explicit selection bypasses heuristic edit-distance gates. Correction-backed targets still require decoder-validated backspace/edit plus suggestion selection. Ordinary commits and automatic autocorrection may update runtime context/safe touch calibration only; they never persist unigram, bigram, or correction evidence. A one-time policy migration preserves Taught words and historical correction targets, removes passive-only words/bigrams, and preserves touch calibration.
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
