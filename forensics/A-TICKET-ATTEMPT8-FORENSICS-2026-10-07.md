# Forensic report — why lane `a-ticket` was on attempt 8

Run: `prd-frankenkey-typing-intelligence-2026-09-27-s2-20261006-095211` (FrankenKey-autobuild-autocorrect)
Run dir: `/Users/apple/.prd-herdr/runs/FrankenKey-autobuild-autocorrect/prd-frankenkey-typing-intelligence-2026-09-27-s2-20261006-095211`

## Verdict

Attempt 8 was not a coding retry. The A1 implementation was finished, committed, and self-reviewed by ~attempt 5; every relaunch after that was burned on **machinery failures while applying a granted gate waiver and closing the lane**. The engine's bookkeeping (attempt counter, stall ladder, no-progress refusal, lease check) charged each of those relaunches as an attempt, so the lane arrived at attempt 8 with `charged: true` on work it no longer needed to do.

## Root cause

`prd_step done` requires gate `g1-unit-suite` to pass. `g1-unit-suite` failed with 6 reds that are all **outside a-ticket's write_paths** — upstream autobuild drift: `CleanModeFleksyLayoutTest`, `KeyboardLayoutSeamTest`, `LanguagePackManagerTest`, `LauncherPrivacyCardTest`, `ReaderAiButtonTest`, `ReleaseUpdaterResourcesTest` (receipt `lanes/a-ticket/receipts/g1-unit-suite.json`, run at 2026-10-06T22:50, exit 1, `base_red: true`, head `dda58cf`).

The boss granted a "stamp done with the recorded FAIL receipt" waiver **three separate times** — `ask-eb49f98cc3` (resolved 10-06T11:13), `ask-ff8b514242` (resolved 10-06T14:01), `ask-85608f8e72` (resolved 10-06T20:42) — but there is no mechanism that applies a granted waiver to the lane: `prd_step done` keeps refusing on the failed gate, the session exits unstamped, the coordinator relaunches, the next session re-runs the gate, gets the same 6 reds, asks the same question again. `gate.json` at close shows `grants: []` and `grants_used: []` — the waiver never landed in the gate state.

## Attempt timeline (from `run.json` `attempt_history` + `grant_history` + incidents)

| Attempt | Candidate fingerprint | Brief rev | What happened |
|---|---|---|---|
| 1a | `30e072c` | 1 | Agent killed by 09:52 daemon restart (error-resolution crew-error:1). |
| 1b | `686202c` | 2 | TerminalStampNeedsBoss — agent absent, no stamped result; parked needs-boss, evidence salvaged (`incidents.jsonl` #1, `scratch/salvage-attempt1-*`). |
| 5 | `c73459a` | 3 | In-scope impl committed. Asked `ask-e0198914fc` (KeyEventHandler write access, granted) then `ask-eb49f98cc3` (6 out-of-scope reds; "stamp done with discover record" granted). `StallLadderExhausted` (incident #4): relaunch refusals charged restarts while parked. Grant crew-error:4 → attempt 6. |
| 6 | `c73459a` | 3 (unchanged) | Same gate reds; `ReplanRequired` — 3 same-brief relaunches refused (incident #7). `OperatorReplanWaiver` accepted one more same-brief relaunch (incident #8). `ask-3ba9a4447e` (gate executor lacked `sdk.dir`) answered "gate-executor fix + retry"; `ask-ff8b514242` waiver granted again. Grant crew-error:6 → attempt 7. |
| 7 | `c73459a` | 3 (unchanged) | Stamped `result.7.bak` status done @ c73459a but gate still red — same blocked exit. BudgetExhausted → d-7223e2d8a0fa "retry". Grant crew-error:11 → attempt 8. |
| 8 | `c73459a` start → `dda58cf` | 4 (template-only diff: added Agent-profile boilerplate; same Change section) | Relaunch found uncommitted attempt-7 changes → `UncommittedWorkPreserved` (`uncommitted-attempt-7.patch`). Re-ran gate, same 6 reds, raised `ask-85608f8e72` — the *third* identical waiver ask — granted 20:42. Session self-reviewed (found+fixed NPE `1910266`, board commit `dda58cf`) then exited unstamped; `lease-check` failed (no lease file, `lease-check.json`). Grant crew-error:17 (d-00890807536f) → attempt 9. |
| 9 | `dda58cf` | 5 | `REPLAN a-ticket ACCEPTED` (d-b1e6caba85e1, inbox seq 30). `d-lease-a-ticket` "fix the lease/ownership and relaunch". Stamped done attempt 9 "handed-off → integration" at `dda58cf` with FAIL receipt recorded. Lane status `landed`. |

## Findings

- **F1 — waiver has no application path.** "Grant gate waiver / stamp done with FAIL receipt" was granted 3× over ~9.5 hours. Nothing writes the grant into `gate.json` (`grants_used` empty at close) or lets `prd_step done` accept a recorded-FAIL gate. Every relaunch re-derived the same reds and re-asked the same question (`ask-eb49f98cc3` → `ask-ff8b514242` → `ask-85608f8e72`, nearly verbatim). Resolution came only when a `REPLAN` + repair-relaunch grant + manual lease fix let attempt 9 stamp done with `notes` carrying the waiver, i.e. the lane finally worked around the missing mechanism.
- **F2 — attempts charged for engine stalls, not lane work.** Fingerprints prove it: attempts 5–7 all show candidate `c73459a` and brief rev 3 — four charged attempts that produced zero new commits. The charged work (daemon restart, absent-agent salvage, stall-ladder refusal, missing lease, gate-env `sdk.dir` defect) is all engine/coordinator side.
- **F3 — relaunch refusal classes loop without changing state.** `StallLadderExhausted` → grant → `ReplanRequired` → `OperatorReplanWaiver` → `NoProgressRelaunch` (brief+candidate identical) → grant. Each refusal's remedy was another boss decision to permit the identical relaunch; the decisions are the loop, not the exit.
- **F4 — attempt 8's brief rev 4 changed only template boilerplate** (`Agent profile` + operator rules sections; diff of `task.7.md`→`task.8.md`). The `ReplanRequired`/`NoProgressRelaunch` fences hash the whole brief, so a template regeneration satisfied "brief changed" without touching the Change section — the fence's intent (make the relaunch different) was technically met but substantively bypassed.
- **F5 — session hygiene added noise.** Attempt 7 left uncommitted tracked changes (salvaged to `uncommitted-attempt-7.patch`); attempt 8 left the worktree missing its `lease` file and with untracked `.crew-scope`/`lease` (forensics snapshot meta `dirty: true`, `lease-check.json` fail) — triggering the `d-lease-a-ticket` decision cycle.
- **F6 — the lane's actual work was done early.** `git log` on the lane worktree: A1 implementation commits `2e6a639`→`af89e63`, sync commit `c73459a`. Everything after is closure mechanics. Final state: `status: landed`, head `dda58cf`, result.json attempt 9 "handed-off → integration".
- **F7 — lease kill loop on attempts 8–9 (notify-log evidence).** Boss notify-log 22:52:47: "a-ticket is in a kill loop: attempt-8 worker (pid 9724) died post-lease-write, attempt-9 (pid 63044) same". The backend pid was killed each time the lease was written, so every relaunched worker exited lease-less and `lease-check` failed the parked lane again. At 22:57 the worker reported tree clean, HEAD `dda58cf` pushed, `prd_step done` still refusing; it stamped `result.json` manually — the stamp the engine needed was administrative, not work.

## What actually unblocked it

Not a new attempt — three boss actions landed in sequence on 10-06T22:47–23:03: `d-lease-a-ticket` (repaired the missing lease), grant crew-error:20 (relaunch to attempt 10 authorized), and `replan_waiver` `REPLAN a-ticket ACCEPTED`. With a valid lease and a replan on record, the attempt-9 session stamped `done` explicitly noting the FAIL receipt and the granted waiver in `notes`.

## Recommendations (machinery, not this lane)

- **R1.** Give "stamp done with recorded FAIL receipt" an executable path: when a waiver ask resolves that way, write it into `gate.json`/`grants_used` or issue `crew stamp-result` directly instead of relaunching the lane to re-derive the same failure.
- **R2.** Don't charge `charged: true` attempts for engine-side exits (absent agent, no lease, stall-ladder, missing gate env). Those are infrastructure retries.
- **R3.** Make `ReplanRequired`/`NoProgressRelaunch` compare only the Change/Acceptance sections, or treat a granted waiver as a plan-affecting change — current whole-brief hash was satisfied by boilerplate churn.
