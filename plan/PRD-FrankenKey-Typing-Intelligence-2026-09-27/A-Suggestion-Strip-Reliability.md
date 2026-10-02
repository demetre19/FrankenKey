# Lane A Handoff PRD — Suggestion Strip Reliability and Shared Seams

Date: 2026-09-27
Lane: A (foundation)
Status: Scope decided; it needs no policy approval beyond the `suggestions/AGENTS.md` acceptance-rule update in §8.
Depends on: nothing.
Blocks: B3, C3, C4, D2, D4 (through Stage A0 only). Once A0 merges, every other stage in this lane runs in parallel with every other lane.
Read first: `00-Overview-and-Philosophy.md` (principles 1, 9, 10; §5–§7).

## 0. Handoff essentials

| Item | Value |
|---|---|
| Source checkout (Mac mini) | `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect` |
| Base | `autobuild/frankenkey-autocorrect-suggestions` @ `57f1356` (2.0.119 / vc170) |
| Lane branch / worktree | `ti/lane-a` / `../FrankenKey-ti-a` |
| DOX chain to read before editing | source `AGENTS.md` → `srcs/AGENTS.md` → `srcs/juloo.keyboard2/AGENTS.md` → `srcs/juloo.keyboard2/suggestions/AGENTS.md`; `test/AGENTS.md` |
| Primary files | `suggestions/CandidatesView.java`, `suggestions/SharedDecoder.java`, `suggestions/Decoder.java` (`RequestKey` only), `KeyEventHandler.java` (`suggestion_entered`, `suggestion_swiped_up`), `Keyboard2.java` (`decoder_state_changed`), `Config.java` (`IKeyEventHandler`) |
| Tests | `test/juloo.keyboard2/suggestions/CandidatesViewPresentationTest.java`, `SharedDecoderTest.java`, `test/juloo.keyboard2/KeyEventHandlerAutocorrectContractTest.java`, `KeyEventHandlerLearningContractTest.java`, `androidTest/.../autocorrect/RealImeThroughputInstrumentedTest.java` |
| Must not | bump the version, build/upload the canonical APK (integrator only), weaken any stale-publish protection, or change ranking |

## 1. Objective

The first tap on a visible suggestion inserts it exactly once, every time. The strip stops blanking between keystrokes. Other lanes get stable extension seams, so they can add new kinds of candidates and gestures without editing the tap logic.

## 2. Current behavior (2.0.119) and root cause

1. `SharedDecoder.request()` (`SharedDecoder.java:420`) creates a new `RequestKey` with an incremented `requestGeneration` for every word snapshot. Snapshots are published on every typed character, cursor moves that leave the word, and every editor refresh (`CurrentlyTypedWord.java:153-216`), including refreshes where the text did not change.
2. Each new request posts a `PENDING` presentation (`SharedDecoder.java:440-446`). `Keyboard2.decoder_state_changed` → `CandidatesView.set_decoder_state` → `clear_candidates()` sets `_request_key = null` and hides every view, and only then returns because the state isn't `READY` (`CandidatesView.java:81-86`, `:122-147`). **The strip blanks on every keystroke.** A tap on a blanked view does nothing, because `_request_key == null`.
3. A tap that reaches `onClick` just before the `PENDING` post carries the previous key. `KeyEventHandler.suggestion_entered` calls `commit_pending_replacement()` first. That can itself trigger an editor mutation and therefore a new request. Only then does it check `_decoder.is_current(key)`, which requires `_latestKey.equals(key)` over all seven fields (`SharedDecoder.java:1093-1097`), and returns silently if the check fails (`KeyEventHandler.java:545-550`).
4. In `onTouch` (`CandidatesView.java:386-418`), a touch with `|dy| ≥ 24dp` on `ACTION_UP` is treated as a swipe. An upward drift toggles learn/unlearn (opening the review dialog) and a downward drift enters the word. With a thumb, a 24dp drift is common.
5. There is no success feedback, so a dropped tap and a successful tap feel the same until the text appears.
6. **Silent failure inside the commit.** Even with a current key, `commit_correction` returns `false` with no feedback in these cases:
   - the tracked word doesn't match the editor: it calls `refresh_current_word()` and gives up;
   - the word is `INCOMPLETE`;
   - `replace_surrounding_text` fails verification.

   (`KeyEventHandler.java:764-797`.) The refresh republishes the strip, so the next tap usually works. This is very likely the "tap it a few times" symptom in web views and slow editors.
7. **Wrong-word risk.** `onClick` reads `_items[item_index]` at *release* (`CandidatesView.java:363-377`). If a new `READY` re-renders the strip between finger-down and finger-up, the slot may now hold a different word, and that word is inserted.

Note on (2): with coalesced delivery (`SharedDecoder.java:1100-1133`), a result that finishes before the main thread runs the posted `PENDING` replaces it, so blanking shows up mainly on slower decodes: separator escalation, and nearby/Hunspell passes for unknown words. The fix is the same either way.

## 3. Scope

In scope:
- Stable strip presentation.
- Acceptance by content-validated ticket.
- Gesture thresholds.
- Exactly-once insertion.
- Accept haptic and visible reject feedback.
- Local debug counters.
- **Stage A0 seams** for Lanes B, C, D.
- The replay-test harness that every lane extends.

Out of scope:
- Ranking, learning policy, new candidate content (B/C/D), and grammar (E).
- Redesigning the strip's look beyond the pressed state and the reject cue.

## 4. Functional requirements

### A-F1 Stable strip (no blanking)
- A `PENDING` presentation never clears candidates that are already visible when it belongs to the same session, editor connection, layout/config/resource epochs, **and the same word slot** (same absolute word start; or, when unreadable, the new word still extends or edits the ticket word with no separator typed in between). The previous `READY` items stay visible and tappable.
- When the word slot changes (a separator was typed, the cursor moved to another word, or a suggestion was committed), old-word candidates are removed immediately. They must never be tappable against the next word.
- If a pending request stays unresolved for more than 150 ms, the visible items may be drawn at 70% alpha. They stay tappable, and the alpha returns to 100% when the next `READY` arrives.
- The strip is cleared only when: the session ends or the input view finishes; the editor connection changes; the layout, config, or resource epoch changes; the word becomes empty and no next-word result is pending; or a `READY` result with zero candidates arrives.
- `EMPTY` (for example suggestions turned off, or an ineligible editor) still clears immediately.

### A-F2 Content-validated acceptance ticket
- Each candidate view holds a `CandidateTicket` created when its `READY` presentation is rendered:
  `{sessionEpoch, layoutEpoch, configEpoch, resourceEpoch, connectionId, word (exact editor text), cursorRelative, hasSelection, absoluteWordStart (if readable), candidate surface, role, sourceRequestKey}`.
- **The ticket is captured at `ACTION_DOWN`** for the slot under the finger, and that ticket is what gets accepted on release, even if the strip re-rendered during the press. The user gets the word they saw when they pressed.
- On tap, `KeyEventHandler` takes a fresh `CurrentlyTypedWord.Snapshot` **before any other mutation** and accepts when all of the following hold:
  - session, layout, config, and resource epochs are equal;
  - the connection is the same;
  - there's no selection;
  - the cursor is still **inside the same word slot**: `absoluteWordStart` is equal when both are readable, or else the current word starts with the ticket word or the ticket word starts with the current word, with no separator in between.
- **Intent wins.** The candidate replaces the *current* word in that slot, even if the user typed or deleted letters after the strip was rendered. Example: the strip showed `their` for `th`; the user typed `e`, then tapped `their` → `their␣`. This matches what the user saw and chose.
- The ticket is rejected only when the slot changed: a separator was typed, the cursor moved to another word, the connection changed, or a selection is active.
- `requestGeneration`, `wordRevision`, and `personalizationEpoch` are **not** part of acceptance.
- `commit_pending_replacement()` runs **after** validation. After it runs, re-validate only the current word slot. The pending replacement concerns the previous word, so a change there must not reject the tap.

### A-F2b No silent commit failure
- If `commit_correction` would fail because the tracked word doesn't match the editor, or because the word is `INCOMPLETE`:
  1. Refresh synchronously (one bounded editor read, ≤64 characters around the cursor).
  2. Re-run the slot check.
  3. If it still passes, retry the replacement **once**.
- If the retry fails, or `replace_surrounding_text` verification fails, show the A-F3 reject cue. No tap path may end in a bare `return` without either inserting text or showing the reject cue.
- The learning and commit tokens (`prepare_commit`, `prepare_selected_correction`) are prepared against the ticket's `sourceRequestKey` result. A new `SharedDecoder.prepare_commit_for_ticket(session, ticket, text, source)` accepts a result whose word fingerprint matches the ticket, even when a newer request with the same content exists. If no retained result matches, the text is still inserted, but it carries no learning token; the failure is on the learning side only.
- Publishing is unchanged: only the exact current `RequestKey` may publish a new presentation.

### A-F3 Visible rejection
- If validation fails (the word slot changed between render and tap), or A-F2b's single retry fails, nothing is inserted, the tapped view plays a 120 ms horizontal shake (skipped if animations are disabled), and there is a light "reject" haptic. A silent return is forbidden.
- Every rejection increments a local counter (A-F7).

### A-F4 Gesture thresholds
- **Tap:** `ACTION_UP` with `|dx| < 32dp` and `|dy| < 40dp`, or any movement that doesn't qualify as a swipe below.
- **Horizontal page swipe:** `|dx| ≥ 32dp` and `|dx| ≥ 1.5·|dy|`.
- **Vertical swipe** (existing learn/enter gestures): `|dy| ≥ 40dp` and `|dy| ≥ 2·|dx|`.
- Handle `ACTION_CANCEL`: it clears the down state and performs no action.
- Swipe-up to learn/unlearn applies only to the entered-literal slot and to `WORD` candidates, as today, with the new threshold. When Lane C3 is ON, C decides the literal slot's behavior.

### A-F5 Exactly once
- A ticket is consumed on first acceptance. Any further `onClick` or `onTouch` on the same ticket is ignored, which covers `onTouch` returning false followed by `onClick`, double taps, and taps from two fingers.
- A second tap within 300 ms on a candidate from a **new** ticket is allowed, because it's a genuine next action.

### A-F6 Accept feedback
- A successful acceptance triggers `HapticFeedbackConstants.KEYBOARD_TAP` (respecting the existing vibration setting via `VibratorCompat`) and shows a pressed-state ripple on the view.
- Setting `ti_strip_haptic_accept` (default ON) sits under Smart typing → Suggestions. Summary: "Vibrate briefly when a suggestion is inserted, so you know the tap registered."

### A-F7 Local diagnostics
- Counters kept in memory: accepted, rejected-mismatch, ignored-duplicate, pending-at-tap.
- In debug builds they are written to `Logs` every 50 events. They are never persisted, never sent anywhere, and hold no word text.

### A-F8 Stage A0 seams (for other lanes)
1. **`CandidateRole`.** Promote `CandidatesView.DisplayRole` to a public enum in `suggestions/`, with the roles in overview §7.3 (unused roles render as `WORD`).
2. **`CandidateSource`.** A worker-side interface: `Candidate[] pinned(Request request, EditorContext ctx)`. `SharedDecoder` merges pinned candidates **ahead of** ranked words when it builds the `READY` presentation. The merge is deterministic and deduplicates by NFC surface, and keeps at most six words in total (existing contract). A source runs on the `SharedDecoder` worker, must finish in ≤2 ms, and must not do I/O after warm-up. Registration: `SharedDecoder.register_source(CandidateSource)`.
3. **`EditorContext`.** An immutable object filled on the main thread at request time: editor class (`PROSE`, `EMAIL`, `URI`, `SEARCH`, …, derived from `EditorConfig`), `noPersonalizedLearning`, `textBeforeCursor` (≤256 UTF-16), `textAfterCursor` (≤64), and locale.
4. **Single acceptance entry.** `IKeyEventHandler.candidate_accepted(CandidateTicket ticket, CandidateRole role, String text)` replaces the direct `suggestion_entered` and `suggestion_swiped_up` calls from the view. It dispatches by role to handlers registered in a `SuggestionAcceptor` map. `WORD`, `ENTERED_TEXT`, and `NEXT_WORD` go to the existing path. B, C, and D register their own roles.
5. **Long-press seam.** `CandidatesView` detects a long-press (≥450 ms, no movement beyond the tap slop) and calls `candidate_long_pressed(ticket, role, text)`. Default handler: no-op. Lane C4 implements it.
6. **Backspace and space hooks.**
   - `KeyEventHandler` gains an ordered `BackspaceHook.on_backspace(ctx, isRepeat)` list, returning `handled`. It's called for the discrete key-down only; repeats pass `isRepeat=true`.
   - `Pointers` gains `SpaceGestureHook.on_space_swipe(rawDirection16, ctx)`, returning `handled`. It's consulted with the **raw** 16-way direction **before** `getNearestKeyAtDirection` resolves a value.
     - This is required because unassigned space-bar directions currently snap to the nearest corner action (`Pointers.java:255-277`). A hook at the `KeyEventHandler` level would only ever see `switch_emoji`, `gif`, or `switch_backward`.
   - Both lists default to empty, so behavior is identical. C1 and D3 register hooks.
7. **Learned Words tab host.** Convert `LearnedWordsActivity` into a tab host with the existing content as the "Words" and "Corrections" tabs. Tabs are registered by class, so B, C, and D add tabs without editing the host.
8. **Smart typing settings sub-screen.** Add an empty `PreferenceScreen android:key="ti_smart_typing"` with five `PreferenceCategory` placeholders (hidden while empty) under Typing assistance.

A0 must be a **pure refactor**: every existing test passes unchanged and behavior is identical. It lands first and alone.

## 5. Technical design notes

- In `CandidatesView.set_decoder_state`, handle `PENDING` without calling `clear_candidates()`. Keep `_tickets[]` in step with `_items[]`, rebuild tickets only on `READY`, and when a `PENDING` state belongs to a different session or connection, fall through to clear.
- In `SharedDecoder`, keep the last two `READY` results in a small `_recentResults` ring keyed by `sourceRequestKey`, so `prepare_commit_for_ticket` can find the result the ticket was rendered from. When the current word differs from the ticket word (the intent-wins case), the learning token is prepared as a manual selected correction from the *current* word to the candidate, matching today's `prepare_selected_correction` semantics. Bound it at 2 and clear it on session, epoch, or connection change.
- `connectionId` is a monotonically increasing counter, incremented in `Keyboard2.onStartInput` and whenever `getCurrentInputConnection()` identity changes.
- Get the fresh snapshot at tap time from `_typedword.snapshot()`. If the typed word is `INCOMPLETE`, force a synchronous `refresh_current_word()`. This is bounded, because the editor read is at most 64 chars, and the tap path already reads the editor.
- Keep all ticket construction on the main thread. Tickets are immutable.

## 6. Implementation stages

| Stage | Content | Parallel? | Exit criteria |
|---|---|---|---|
| **A0** | Seams (A-F8), pure refactor | Must land **first, alone** in `ti/integration` | Full `testDebugUnitTest` green; `RealImeThroughputInstrumentedTest` focused green; no behavior diff in the replay suite |
| **A1** | Ticket acceptance (A-F2), exactly-once (A-F5), validation order fix | Parallel with all other lanes after A0 | New tests §7.1–7.5 green |
| **A2** | Stable strip (A-F1), rejection feedback (A-F3) | Parallel with A3 | Presentation tests green; screen recording shows no blank frames while typing "the quick brown fox" |
| **A3** | Gesture thresholds (A-F4), accept haptic plus setting (A-F6) | Parallel with A2 | Gesture tests green |
| **A4** | Diagnostics (A-F7); replay-test harness `ImeReplayInstrumentedTest`, with a script DSL other lanes extend | After A1–A3 | 500-trial tap-during-pending script: 0 dropped, 0 duplicated |

## 7. Tests (new)

1. Tap while a `PENDING` for the same content is outstanding inserts the word once.
2. Tap after a newer `READY` with identical content inserts the word once and keeps the learning token.
3. Tap after more letters were typed **in the same word** (strip computed for `th`, word now `the`, tap `their`) replaces the current word and gives `their␣` once. Tap after a separator or cursor move to another word inserts nothing, shows the reject cue, and increments the counter.
3b. The strip re-renders between `ACTION_DOWN` and `ACTION_UP`: the word under the finger at down is inserted, not the new occupant of the slot.
3c. Tracked word mismatches the editor (simulate a lagging editor): one synchronous refresh + retry inserts once; a second failure shows the reject cue. There is no silent return (assert that every exit path of the tap handler either inserts or shows the cue).
3d. After a separator, old-word candidates are gone before the next-word result arrives.
4. Tap while a pending replacement of the **previous** word commits inserts the word once.
5. Double tap and `onTouch`+`onClick` on the same ticket produce one insertion.
6. A 30dp vertical drift counts as a tap; a 45dp upward swipe on a `WORD` counts as learn/unlearn; a 40dp horizontal drift pages.
7. `ACTION_CANCEL` performs no action.
8. The strip keeps its items across `PENDING`, and clears on an editor change, on a session end, and on an `EMPTY` state.
9. Publish safety: a stale worker result still never publishes (all existing `SharedDecoderTest` stale-publish tests stay unchanged).
10. A0 no-op proof: all existing tests pass unchanged.

## 8. DOX and contract updates (same change)

- `suggestions/AGENTS.md`: replace "Only exact current `RequestKey` may publish, commit, learn/forget, or accept actions; PENDING/EMPTY are inert" with: "Only the exact current `RequestKey` may publish. Candidate acceptance uses a content-validated `CandidateTicket` (session/layout/config/resource/connection epochs plus exact word, cursor, and selection), consumed once. A visible candidate never fails silently. PENDING keeps the prior READY items visible and tappable; EMPTY clears."
- `suggestions/AGENTS.md`: add the `CandidateSource`, `EditorContext`, `SuggestionAcceptor`, and hooks seams to Ownership.
- `srcs/juloo.keyboard2/AGENTS.md`: note the Learned Words tab host and the Smart typing sub-screen.
- Verification lists: add `ImeReplayInstrumentedTest`.

## 9. Risks

| Risk | Mitigation |
|---|---|
| The intent-wins rule inserts a candidate ranked for an older prefix (`th`→`their` after typing `the`) | This is deliberate: the user saw and chose that word. It's limited to the same word slot, and backspace undo (C1) restores the typed word. |
| A lagging editor makes the A-F2b retry read stale text | The retry is limited to one bounded read; `replace_surrounding_text` still verifies before editing; on failure, the reject cue shows instead of risking corruption. |
| Keeping items visible shows a stale ranking for up to one decode (~5–30 ms) | The alpha cue after 150 ms, and the content check guarantees the inserted text is correct for the current word. |
| Seam refactor regresses throughput | A0 exit gate runs the focused throughput test; the hook lists are empty arrays. |

## 10. Changelog copy (user-visible)

- Suggestions now insert on the first tap, even while you're typing quickly. The suggestion bar no longer flickers between keystrokes.
- A tap that can no longer apply (because the word changed) now gives a small shake instead of silently doing nothing.
- A slightly sloppy tap on a suggestion no longer opens the learn/forget prompt.
- New option: a short vibration when a suggestion is inserted.

## 11. Done definition

- All stages' exit criteria are met, DOX is updated, and there's a lane report listing each changed file and test with results.
- The branch is merged to `ti/integration` by the integrator, and replay suite A is green on the integration branch.
