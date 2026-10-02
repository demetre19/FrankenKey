# RESEARCH — Typing Intelligence launch inputs

Date: 2026-09-28
Purpose: Stage 2.5 evidence for `crew-plan-typing-intelligence-2026-09-27.json`. The research corpus is the 2026-09-27 plan bundle itself: `00-Overview-and-Philosophy.md` §1 (root causes with line anchors), §10 (adversarial review log R1–R16), plus the five lane handoff docs. Every claim below was verified against the 2.0.119 source @ `57f1356` on 2026-09-28.

## Verified anchors (re-checked 2026-09-28 against source)

- `srcs/juloo.keyboard2/suggestions/CandidatesView.java:81` — `set_decoder_state` clears `_request_key`/items on `PENDING` (strip blanking).
- `srcs/juloo.keyboard2/suggestions/SharedDecoder.java:1093` — `is_current_locked` exact-key check.
- `srcs/juloo.keyboard2/KeyEventHandler.java:764` — `commit_correction` silent `return false` paths.
- `srcs/juloo.keyboard2/KeyEventHandler.java:2340` — `should_try_autocorrect` checks typing-assistance only (email-field autocorrect live bug).
- `srcs/juloo.keyboard2/EditorConfig.java:183` — `is_structured_text_editor` (email/URI statelessness).
- `srcs/juloo.keyboard2/CurrentlyTypedWord.java:455` — `is_word_char` excludes `@` and `.`.
- `srcs/juloo.keyboard2/Pointers.java:255` — `getNearestKeyAtDirection` ±3-of-16 snap (unassigned space directions are not free).
- `srcs/juloo.keyboard2/suggestions/Decoder.java:498` — `decode_next_words` exists (C5 extends it).
- `srcs/juloo.keyboard2/suggestions/PersonalizationStore.java:418` — replacement-rule targets restricted to single learnable words (D needs its own store).
- `srcs/juloo.keyboard2/SettingsBackup.java:92` — export covers all default prefs (B needs a separate named store).
- `res/xml/bottom_row.xml:6`, `res/xml/clean_text.xml:41` — space-bar corner/direction assignments.
- `srcs/juloo.keyboard2/Keyboard2.java:484` — reader/assistant strip region (grammar merge point).

## Prior art

- Tap-drop failure modes are three distinct silent paths (overview §1/R1–R3), not one bug: blanked-GONE views, exact-RequestKey rejection, and silent `commit_correction` returns.
- `PendingReplacement.undoToken`/`learnSourceOnUndo` (`KeyEventHandler.java:126-152`) is unused plumbing built for exactly the C1 undo window — C1 consumes it rather than adding new state.
- `Decoder.decode_next_words` already merges personalization pairs with the language pack; C5 extends ranking inputs, no new decoder path.
- The system grammar checker (`SystemGrammarChecker`) and `AssistantStripView` Fix/Dismiss prompt already exist; E merges offline rules into that prompt and reuses `Correction.apply` revalidation.
- Reader AI (encrypted key, model setting, disclosure v3, bounded client, quick surface host) is the entire substrate for E-F4.
- Replacement rules (`PersonalizationStore`) can only rewrite to a single learnable word — they are not a shortcut store.
- Existing 2026-07 learned-typos incident (`CHANGELOG-learned-suggestions-unlearn-2026-07-07.md`) is the explicit prior art for the explicit-only learning policy C replaces.

## Alternatives

- Separate AutoBuild runs per section — **rejected**: cross-run `depends_on` releases only on `done+promoted`, and promotion merges to the source branch, which root AGENTS.md reserves for separate operator approval. One Crew run with a lane DAG preserves both the wave model and the approval gates.
- Sequenced launches A→B→C→D→E — **rejected**: serializes what the plan explicitly parallelizes (B1/B2, C0–C2, D1a, E) and adds ~19 agent-days to the critical path.
- Keep `expose_learn_action`'s `📖+` literal slot alongside C3's quoted `KEEP_TYPED` — **rejected**: two learn affordances in one slot; C-F3 replaces it only while its switch is ON.
- In-editor ghost text for completion — **rejected** (overview §9): composing-span styling varies by app; strip `COMPLETION` rendering instead.
- IME-action (Send) grammar trigger — **rejected** (R14): text has already left the field; word-boundary window replaces it.

## Gotchas

- `PENDING` blanking is coalesced-visible mainly on slow decodes — the fix is the same either way (A-F1).
- Autocorrect is asynchronous (latent boundary path): the undo window must open when the correction lands, not when the separator is typed (R10).
- `getNearestKeyAtDirection` snaps unassigned space swipes to corner actions within ~±67°, so "free" directions don't exist — D3 must hook the raw direction before the snap (R9).
- `SettingsBackup` exports all default prefs — any new sensitive store must be a named file outside it (B).
- Email hint text ("Email subject") can false-positive prose fields — strict single-line hint regex only (R7).
- Credential-protected prefs are unreadable before first unlock — stores must fail closed and retry after `ACTION_USER_UNLOCKED` (R8).
- Whole-field `commitText` flattens rich spans — AI replace must be minimal back-to-front segment edits (R15).

## Assumptions

| Assumption | Load-bearing | What evidence would change this |
|---|---|---|
| One Crew run with 8 lanes on one integration branch is the intended launch shape | High — determines merge/wave mechanics | Operator corrects to per-section runs or a different integration target |
| Q1–Q5 recommended defaults are adopted (no extra D switches; C defaults 1–5 ON/6 OFF; contextual down-swipe; prose email recall; separate writing model) | Medium — changes settings surface and D3/D4 gesture scope | Operator answers an open question differently; plan amendment |
| `promote` must stay off (commits to source branch need separate approval) | High — without it, queued dependents can't release | Operator authorizes promotion for this run |
| Integrator role = operator/integrator step after run close (version bump, signed APK, phone upload, CHANGELOG/README/fastlane) | High — lanes are forbidden these surfaces | Operator delegates integration to the run |
| Emulator verification via replay suite + focused instrumented tests inside the run; live phone proof deferred to Test Builds | Medium | Operator requires emulator smoke inside lanes |

## Premortem

- **Stage-lane granularity drift**: the machinery `depends_on` is per-lane, so `b-email` starting only after `a0-seams` means B1/B2 begin slightly later than the paper plan's "immediately". Accepted: bounded by one seam merge.
- **Shared-file merges**: `Keyboard2.java`, `KeyEventHandler.java`, `settings.xml`, `strings.xml` are declared `shared_paths` pairs; conflicts are textual but real — the integrator merges in wave order A→C→B→D→E.
- **C1 is the critical-path hinge**: `d-power-gestures` cannot launch until C1 verifies. A C1 stall parks D's back half while D-store/D0 still proceed.
- **Replay harness is Wave-3 instrumentation**: A4's `ImeReplayInstrumentedTest` is a lane deliverable; if it slips, B5/D-replay acceptance blocks without blocking B1–B4.
- **Gradle gating**: `testDebugUnitTest` on the integration tree is heavy; a flaky pre-existing test would stall every lane's verify — preflight records baseline state rather than re-diagnosing per lane.
