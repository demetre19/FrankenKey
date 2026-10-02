# PRD: FrankenKey Typing Intelligence — Section A: Suggestion Strip Reliability and Shared Seams

Date: 2026-09-27 (launch 2026-09-28)
Status: **Approved.** Scope decisions recorded by the operator 2026-09-27 (needs no policy change beyond the `suggestions/AGENTS.md` acceptance-rule update). Launch authorized 2026-09-28 ("write a PRD for each section and launch").
Source repo: `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect`, base `autobuild/frankenkey-autocorrect-suggestions` @ `57f1356` (2.0.119 / vc170).

## 1. Goal

The first tap on a visible suggestion inserts it exactly once, every time. The strip stops blanking between keystrokes. This section also lands the shared extension seams (CandidateRole, CandidateSource, EditorContext, SuggestionAcceptor, BackspaceHook, SpaceGestureHook, Learned Words tab host, Smart typing settings sub-screen) that the B, C, and D sections build on.

## 2. Canonical specification

`plan/PRD-FrankenKey-Typing-Intelligence-2026-09-27/A-Suggestion-Strip-Reliability.md` is the binding spec for this section (requirements A-F1 through A-F8, stages A0–A4, tests, DOX lines, changelog copy). The overview `00-Overview-and-Philosophy.md` principles 1, 9, 10 and §5–§7 bind as well. This file is the run-facing contract; where the two differ, the lane doc wins on scope detail and this file wins on launch mechanics.

## 3. Scope

In scope, exactly as spec'd:
- Stage A0 seam refactor (A-F8): `CandidateRole`, `CandidateSource`, `EditorContext`, `candidate_accepted(ticket, role, text)` with `SuggestionAcceptor`, `candidate_long_pressed` seam, `BackspaceHook` list, `SpaceGestureHook` in `Pointers` (raw direction, pre-snap), Learned Words tab host, empty Smart typing sub-screen. Pure refactor: every existing test passes unchanged.
- A1 ticket acceptance (A-F2): `CandidateTicket` captured at `ACTION_DOWN`; content-validated slot check (epochs, connection, no selection, same word slot); intent-wins replacement; `commit_pending_replacement()` after validation; `prepare_commit_for_ticket`.
- A2 stable strip + visible rejection (A-F1, A-F2b, A-F3): PENDING keeps visible items; old-word candidates cleared on slot change; 150 ms/70% alpha cue; synchronous-refresh + one-retry on commit mismatch; 120 ms shake + reject haptic instead of any silent return.
- A3 gesture thresholds (A-F4) and accept haptic + `ti_strip_haptic_accept` setting (A-F6).
- A4 diagnostics counters (A-F7) and the `ImeReplayInstrumentedTest` script DSL every lane extends.

Out of scope: ranking, learning policy, new candidate content (B/C/D), grammar (E), any visual redesign beyond pressed/reject states.

## 4. Constraints

- Source DOX chain read before editing: source `AGENTS.md` → `srcs/AGENTS.md` → `srcs/juloo.keyboard2/AGENTS.md` → `srcs/juloo.keyboard2/suggestions/AGENTS.md`; `test/AGENTS.md`.
- Never weaken stale-publish protection (only the exact current `RequestKey` publishes).
- Worker commits land on the engine-assigned lane worktree/branch only; merges to the run integration branch follow the orchestrator's wave order.
- **Never** bump `versionCode`/`versionName`, build a signed release APK, replace the canonical APK, or upload to the phone — integrator/operator only (root Release Contracts).
- DOX updates in §8 of the lane doc land in the same commit as the behavior.

## 5. Acceptance criteria

1. A0 is a pure refactor: all pre-existing unit tests pass unchanged; `RealImeThroughputInstrumentedTest` focused run is green; no behavior diff.
2. A tap during a same-slot `PENDING` inserts once; a tap on a slot that changed shows the reject cue and inserts nothing; no tap path ends in a silent `return`.
3. Intent-wins: strip computed for `th`, word now `the`, tap `their` → `their␣` exactly once, including across a mid-press re-render.
4. Strip does not blank between keystrokes; it clears only on session/connection/epoch/slot change or EMPTY.
5. Gesture thresholds: 30 dp vertical drift is a tap; ≥40 dp vertical swipe = learn/enter; ≥32 dp horizontal = page; `ACTION_CANCEL` no-ops.
6. Successful acceptance plays `KEYBOARD_TAP` haptic gated by `ti_strip_haptic_accept` (default ON, under Smart typing → Suggestions).
7. Exactly-once: duplicate `onTouch`/`onClick`/two-finger taps on one ticket produce one insertion; a fresh ticket after 300 ms is a real new action.
8. `ImeReplayInstrumentedTest` 500-trial tap-during-pending script: 0 dropped, 0 duplicated insertions.
9. `suggestions/AGENTS.md` acceptance-rule line replaced with the CandidateTicket contract and the seams added to Ownership, in the same commit.

## 6. Changelog copy

Owned by `A-Suggestion-Strip-Reliability.md` §10 (integrator assembles at release).

## 7. Dependencies

Depends on: nothing. Blocks: every B/C/D stage that renders or accepts a new candidate role (via A0), and C3/C4 (via A1 tickets). Machinery encoding: lanes `a0-seams` and `a-strip` in `plan/PRD-FrankenKey-Typing-Intelligence-2026-09-27/crew-plan-typing-intelligence-2026-09-27.json`.
