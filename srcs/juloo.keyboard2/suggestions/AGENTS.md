# suggestions DOX

## Purpose

- Own the shared suggestion/autocorrect decoder, asynchronous request controller, personalization ranking, touch geometry, and candidate presentation.

## Ownership

- `Decoder` is the sole synchronous scoring policy; `SharedDecoder` owns worker serialization, resource lifetimes, stale-result rejection, and immutable presentations.
- Keyboard event routing and IME session/layout/resource integration live in the parent package.

## Local Contracts

- Suggestions and separator-time autocorrect derive from the same immutable request; no synchronous commit-time decode is allowed. Ordinary typing uses a bounded fast candidate preview and may finalize only exact proven learned corrections. A separator escalates that exact request to the full nearby/Hunspell pass, and a provisional preview result must never settle the pending boundary.
- An unknown three-code-point separator boundary may publish an early result only for a complete-evidence, same-length, one-substitution Cdict winner that clears explicit frequency, literal, and runner-up margins. That early winner must equal the exhaustive provider result; all other word lengths proceed directly to the full boundary pass.
- If the exact separator-boundary result is still pending, commit the literal separator immediately. A late full correction may refine only the same request's exact source-plus-separator after revalidating its session, InputConnection, cursor, and suffix. A key-down event alone keeps that proven boundary live so the full result may resolve after the key mutates the editor; any unrelated editor text or cursor mutation freezes the literal text.
- Completed boundary results are retained in a bounded worker queue so a separator can consume its exact request after newer typing begins. The key-event layer may retain at most the two immediately preceding word boundaries and may replace only an unchanged absolute editor range with no more than two following words; raw/unreadable editors remain excluded. The visible suggestion strip still publishes only the exact current request.
- A complete editor refresh for the same normalized word, cursor, and code-point count preserves the locally captured touch trace. Any changed word, cursor, or length clears that evidence; capped incomplete readback remains fail-closed.
- Native candidate recall is bounded, Unicode-scalar aware, coordinate-first, and scored against the exact visible keyboard layout. An unknown source completes the bounded nearby and Hunspell recall passes before final selection even when the first native beam already produced a plausible candidate; recognized two-letter fallback literals also receive the bounded Hunspell repair pass.
- Native result truncation is not a blanket autocorrect veto: a clear recognized one-edit winner may commit, while two-edit repairs still require complete evidence and resource/corruption failures fail closed. A complete recognized one-edit omission or extra-tap repair backed by both Hunspell and an exact main-dictionary entry keeps its edit cost without a second generic length-delta penalty. For unknown sources of at least five code points, a tied omission may outrank multiple equally scored deletions; four-code-point sources require a unique tied deletion so ambiguous inputs such as `thow` stay literal. A dominant repeated-letter omission may beat a substitution only with complete evidence, equal top frequency, and a two-level margin over other omission candidates.
- Only an exact current `RequestKey` may publish, prepare a commit token, learn/unlearn, or receive a candidate action; PENDING/EMPTY states are non-clickable. A prepared token may commit after the typed-word key advances only while its captured session and personalization domain remain valid.
- The IME passively prewarms the exact dictionary, Hunspell, and personalization descriptors before the first editor; same-key resources remain worker-resident across editor sessions, while active resource changes advance through explicit epoch updates.
- Candidate status treats either a loaded main Cdict or the selected locale's bundled Hunspell language pack as installed typing data; never show the install-dictionary banner while a valid matching pack is available.
- Learned words must remain reversible, and ranking must combine dictionary, geometry, frequency, unigram, bigram, and bounded typo-to-target evidence deterministically without user-visible debug labels.
- Locale language packs may provide a bounded `next_words.tsv` previous-word model. Load it on the decoder worker, merge it with exact personalization bigrams, and permit a cold contextual correction only for a unique nearby one-substitution target with explicit weight and margin gates; weak context and valid controls remain literal.
- Candidate UI stays compact: at most three single-line word candidates plus the separate emoji slot unless intentionally redesigned.
- Accepted typo corrections are stored only in credential-protected preferences. Correction-only targets survive restart without becoming unigrams or next-word anchors, and learning remains active when either Suggestions or Autocorrect is enabled.
- Touch calibration is worker-confined with personalization and persists only a bounded sample count plus normalized X/Y offsets in credential-protected preferences. It may learn only from complete accepted literal touch traces, becomes active after the minimum evidence threshold, and must be subtracted from captured touch coordinates before spatial substitution scoring. Clearing personalization removes it; correction-source touches never train it.
- Exact correction identity is NFC-normalized lowercase editor text, distinct from accent-folded dictionary canonical keys; accepted exact pairs may contain at most two textual optimal-string-alignment edits and never consult key geometry.
- Contextual correction evidence is an exact previous-word/source/target triple, never a cross-product of unrelated global pairs and bigrams. One editor-verified correction may replay only in that same context and may cover up to three optimal-string-alignment edits; context-free exact pairs retain the two-edit/four-event policy. Do not hardcode phrases.
- Exact evidence is recalled ahead of related-only evidence, improves ranking from the first acceptance, and after four accepted exact corrections may override a recognized dictionary literal, passive learned-literal history, or a nearer ordinary guess. Before that threshold, recognized and learned literals remain protected. Related same-index adjacent-key evidence is weaker and never unlocks an override.
- Cold all-caps tokens of two or three code points reject every automatic spelling change so acronyms remain typeable; four-code-point all-caps tokens also reject cold one-letter substitutions. Lowercase prose candidates whose provider surface is an all-caps token are not autocorrection targets.
- Lowercase standalone `j` may repair narrowly to capitalized `I`. An unrecognized lowercase two-letter literal may use the ordinary decisive one-substitution path. A lowercase two-letter literal recognized only by the fallback lexicon may use its primary nearby one-substitution repair only when that target is exact in the main dictionary; main-dictionary literals, learned literals, uppercase, mixed-case, punctuation, and technical tokens remain protected.
- A spatially supported adjacent transposition in a three-letter word may outrank weaker lexical guesses. Provider order alone must not resolve tied short repairs, Hunspell primary status must not come from shortening suggestions, and lowercase requests must not promote proper-name casing.
- A missing straight apostrophe before a common contraction suffix may produce a Hunspell-validated contraction candidate. A bounded negative-base morphology may also recover a missing `n` plus apostrophe, such as `doest` to `doesn't`, while ordinary words such as `cat` and `dot` and ambiguous apostrophe-only literals such as `well` remain protected. Exact unlearned `im` may autocorrect immediately to `I'm`; learned `im` remains literal unless four accepted exact `Im` to `I'm` choices establish the apostrophe preference. Every `i'm` candidate must present and commit as capitalized `I'm` for both lowercase and capitalized source input.
- English regular inflection repairs are deterministic transformations over unknown letter-only sources and must validate the generated target against Cdict or Hunspell before ranking. Keep y-to-i forms, sibilant plurals, bounded `-ful`/`-ness` repairs, and doubled-consonant `-ing`/`-ed` decisions explicit; valid source words and ambiguous competing forms remain protected.
- Changed candidate/autocorrect replacements keep their prepared learning token only until the next accepted action. Backspace commits the changed target before ordinary deletion and never restores the misspelled source; cursor movement likewise settles the replacement without restoration.
- Forgetting a learned word removes correction evidence where it is either source or target, and clearing adaptive learning must also clear any inactive prewarmed worker state.

## Work Guidance

- Keep scoring constants, bounds, source merging, confidence margins, and tie-breaking explicit and deterministic.
- Keep Cdict, Hunspell, and personalization access worker-confined through `SharedDecoder`; the main thread must never wait for decoding.

## Verification

- Run `SuggestionPersonalizationTest`, `CdictSpatialQueryTest`, `SharedDecoderTest`, `CandidatesViewPresentationTest`, `AutocorrectScoringTest`, and focused key-event learning/autocorrect tests when relevant.

## Child DOX Index

- No child AGENTS.md files currently. Suggestion classes are owned here.
