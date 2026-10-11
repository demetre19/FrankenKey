# PRD — Typing intelligence: Gboard-grade correction and prediction

Status: in progress · Branch: `ccr-fe400b38-bj1bar` · Owner: FrankenKey

## 1. Problem

Users report three things:

1. FrankenKey **learns words they never asked it to learn**.
2. Autocorrect **misses many mistakes** Gboard catches, especially
   hit-the-key-next-door slips.
3. Gboard **fixes mistakes immediately**. FrankenKey commits the typo and
   replaces it later, if at all.

Gboard is closed source. Its open-source ancestor, AOSP LatinIME (read
via the `Helium314/HeliBoard` fork), plus our own code and a native probe
of `assets/dictionaries/en_US.dict`, gave these root causes:

| # | Root cause | Evidence |
|---|---|---|
| R1 | Every committed literal that Cdict "finds" increments `_word_counts`. That breaks the `AGENTS.md` rule that ordinary commits never save word counts. A learned word then permanently blocks autocorrect, gets a personal-suggestion boost, and appears in Learned words. | `PersonalizationStore.record_commit` → `record_touch_calibration` returns `true` for any uncorrected commit; `Decoder.choose_autocorrection`: `if (literal.unigramCount > 0) return …` |
| R2 | "Recognized" is far too loose. Cdict has about 300k entries (about 84k at frequency 0). It matches lowercase input against acronyms (`nad` → `NAD`, f14) and against whitelist shortcuts (`wierd` → `weird`, `alot` → `a lot`, `cant` → `can't`). Any of these protects the typo from correction. | native probe of `en_US.dict` |
| R3 | A slip to the neighbouring key costs nearly as much as a missed or extra letter (about 4–6.6 Q8 vs 6 Q8). LatinIME makes it about 3× cheaper. About 15 separate vetoes can then cancel the correction. | `Decoder.Geometry.substitution_cost_q8`; LatinIME `scoring_params.cpp` |
| R4 | At the moment of space, only learned or 3-letter corrections are allowed. Everything else is committed as typed and replaced later. | `is_proven_fast_autocorrection`, `is_safe_boundary_autocorrection` |
| R5 | No usable language model. Frequency is 4 bits and nearly flat across the top buckets. Context data is 4,096 Tatoeba n-grams. After space, predictions read only personal word pairs, which nothing ever writes, so the strip is always empty. | `LanguageModel`, `decode_next_words`, `_bigram_counts` has no writer |

## 2. Goals and non-goals

Goals:
- G1: Nothing enters the learned vocabulary without an explicit user action
  (Teach, or a verified backspace-then-select correction).
- G2: Common typos that Gboard fixes (`nad`, `wierd`, `alot`, `cant`,
  neighbour slips like `thw`, `hwllo`) are corrected.
- G3: An explicit right swipe accepts the top suggestion immediately, with
  no late replacement.
- G4: After a word is committed, the strip shows next-word predictions.

Non-goals (this PRD): cloud models, swipe/glide typing, and new languages.
The privacy contracts in `AGENTS.md` are unchanged.

## 3. Right-swipe accept gesture (replaces "commit on space" from the analysis)

User request: instead of making space commit corrections immediately, add
a right swipe that can start anywhere on the keyboard.

- **Trigger:** a single pointer moves at least **3 key widths** to the
  right. One key width is the layout's unit key width.
  - 3 rather than 4: it is about 30% of a QWERTY row. That is far longer
    than any side-symbol swipe (about 0.5 key) and still comfortable to
    do with one thumb.
  - The movement must be mostly horizontal: |dy| ≤ 0.5·|dx|.
  - It fires on touch-up, so a pointer that comes back before release
    cancels it.
- **Start position:** any key. **Exception:** a pointer that has already
  entered slider mode keeps its slider. That covers the space-bar cursor
  slide and any custom slider key. Otherwise cursor movement on space would
  break.
- **Action:**
  1. A word is being typed and the decoder's current result is ready:
     commit the decoder's autocorrection if it has one, otherwise the
     first suggestion, otherwise the typed word. Then add a space. This
     uses the same commit path as tapping a suggestion, so undo,
     personalization tokens and the stale-result checks are unchanged.
  2. No word is being typed: insert a space, exactly like the space key.
     A whole predicted word is never inserted by accident.
  3. The result is pending or the editor can't be read: behave exactly like
     the space key, so the existing late correction still applies. Never
     wait on the main thread.
- **Feedback:** the same vibration as the existing vertical keyboard swipes.
- **Conflicts:** none of the side symbols are emitted. The side symbol
  previewed during the drag is discarded, the same way vertical keyboard
  swipes discard it.

## 4. Work plan (ordered by impact ÷ effort; each part is pushed when green)

### Part 1 — Stop passive learning (R1) · easy · very high impact
- `record_commit` updates only context and touch calibration; it never
  increments `_word_counts`.
- Bump `VOCABULARY_POLICY_VERSION` to 2. The existing migration then
  removes words that were neither taught nor correction targets, and keeps
  touch calibration.
- Tests: a recognized ordinary commit leaves `word_count` at 0, nothing
  appears in `learned_words()`, and taught words survive the migration.

### Part 2 — Right-swipe accept (G3) · easy–medium · high impact
- `Pointers`: add `isRightAcceptSwipe(ptr)` plus a `set_key_unit_width`
  setter fed by `Keyboard2View`. Add `onKeyboardSwipeRight()` to
  `IPointerEventHandler`.
- `KeyEventHandler.keyboard_swiped_right()` implements §3.
- Tests: a Robolectric `Pointers` test covers the threshold, slope, and
  slider exemption; a handler test covers accept, fallback-to-space, and
  the no-word case.

### Part 3 — Weak recognition and whitelist corrections (R2) · medium · high impact
- **Whitelist:** a Cdict alias hit (`index != original_index`) whose
  target differs from the typed word by more than case becomes a
  `SOURCE_WHITELIST` candidate. It autocorrects with LatinIME's
  `KIND_WHITELIST` priority: it overrides a Hunspell-valid literal, but not
  taught words, replacement rules, or MIXED/ALL-CAPS input. It also counts
  as a "proven fast" correction, so space applies it immediately.
- **Acronym-only match:** lowercase input whose only Cdict match is an
  all-caps entry, with no Hunspell match, is unrecognized
  (`nad` → `and`).
- **Frequency-0-only match:** a literal recognized only by a frequency-0
  Cdict entry, not by Hunspell and not learned, is unrecognized.
- Tests: in `AutocorrectScoringTest`, `nad`→`and`, `wierd`→`weird`, and a
  taught `wierd` stays `wierd`.

### Part 4 — Next-word predictions from the bundled model (R5a) · easy · medium impact
- `decode_next_words` merges the personal word pairs (ranked first) with
  the bundled `LanguageModel` word pairs and triples for the previous word.
- Tests: after committing `I`, the strip contains words from the bundled
  model.

### Part 5 — Rebalance the key-position model (R3) · medium · high impact, needs tuning
- With touch data: spread 0.55 → 0.8 key, scale 2.0 → 1.0. Without touch
  data: neighbour cost 4 → about 2 Q8 (halve the squared distance).
  Omission, extra-tap and transposition costs are unchanged.
- Validate against `gboard_autocorrection_oracle.json` and
  `swiftkey_autocorrection_oracle.json` (`SwiftKeyParityInstrumentedTest`).
  No regression in the harmful-correction count is acceptable.

### Part 6 — Collapse the veto ladder (R3/R4) · large · high impact
- Replace the runner-up and length-repair vetoes with one confidence score,
  modelled on LatinIME's normalized score versus a threshold: (literal −
  best) margin minus (best − runner-up) ambiguity, with a fixed threshold.
  Keep the safety rules that protect user data: taught words, replacement
  rules, casing, protected literals.
- Gate on the instrumented parity suites; this needs the emulator.

### Part 7 — Real language model (R5b) · large · highest long-term impact
- Ship a pruned word-pair/word-triple model (licence-clean corpus) and
  8-bit log-probability unigrams. Weight language roughly equally with key
  distance (LatinIME `DISTANCE_WEIGHT_LANGUAGE` ≈ 1.12). Use context to fix
  correctly spelled wrong words (`fro` → `for`, "I'm form" → "from").
- This needs corpus and licence decisions, so it is planned here and done
  as separate work.

## 5. Verification

- Each part runs its focused Robolectric tests:
  `gradle testDebugUnitTest --tests <Class>`.
- Parts 5 and 6 also need the instrumented `RealImeThroughputInstrumentedTest`
  and `SwiftKeyParityInstrumentedTest` on the `FrankenKeyParity` emulator
  (`AGENTS.md`). Those can't run in the cloud container and must be run on
  the Mac before a release.
- Release builds follow the signed `assembleRelease` + `verifyReleaseIdentity`
  flow; that is not done from the cloud container.

## 6. Status

| Part | State |
|---|---|
| 1 Stop passive learning | in progress |
| 2 Right-swipe accept | planned |
| 3 Weak recognition + whitelist | planned |
| 4 Next-word from bundled model | planned |
| 5 Key-position rebalance | planned |
| 6 Collapse veto ladder | planned |
| 7 Real language model | planned (separate work) |
