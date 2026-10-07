# IMPROVEMENT — prd-frankenkey-typing-intelligence-2026-09-27-s2-20261006-095211

Verdict: mechanical=fail, process=fail. Integration head 5e1d5ec is a *partial* merge: 3 landed lanes (a-ticket, a-stability, a-haptic) + ver-s2; 14 planned lanes cancelled without authorized scope drops; 810 unclassified foreign writes. The product diff (+5848/-297, CandidateTicket, stability slots, grammar corpus, gate fixes) is coherent — the failures were machinery failures.

## Defect classes (root causes)

- **C1 preflight-env-no-repair** — 4 launches refused on absent `local.properties`/sdk.dir (crew-error:30,37,38; obs #22,#23); deterministic fix waited on human round-trips.
- **C2 baseline-gate-wedge** — 6 out-of-write_paths test reds re-litigated 4× (ask-eb49f98cc3, ask-ff8b514242, ask-85608f8e72, ask-9b81197e3c); waivers live in chat, not the gate ledger.
- **C3 unmapped-stall-loop** — a-ticket: 9 attempts, StallLadderExhausted + BudgetExhausted + ReplanRequired + NoProgressRelaunch all "unmapped class"; same-brief relaunches repeated the same gate failure.
- **C4 dispatch-retry-no-backoff** — DriveError storm: verbatim retries against saturation, stale-attempt, cancelled-lane refusals; step cap 50 hit twice (crew-error:9,26,27,28,35,36).
- **C5 foreign-write-attribution** — 810 seqs flip process=fail but mix bookkeeping with real violations; no actionable list.
- (Lease/preflight wedge D5 folds into C1/C5 mechanics: 3 no-lease blocks + 1 live-lease refusal.)

## Solution classes

- **S1** preflight materializes declared env prerequisites with a repair step (retires C1).
- **S2** durable gate-waiver receipts consumed by `done` and the stall mapper (retires C2, C3).
- **S3** classified dispatch refusals: backoff / attempt-resolve / scope-drop routing (retires C4).
- **S4** foreign-write attribution: bookkeeping vs violation at record time (retires C5).

## Proposals

| P | Title | Sev | Diff | Route |
|---|-------|-----|------|-------|
| P1 | Preflight env materialization (sdk.dir auto-repair) | high | easy | factory, blocking |
| P2 | Gate waiver receipts in lane ledger | high | medium | factory |
| P3 | Classified dispatch refusals | medium | medium | factory |
| P4 | Foreign-write attribution | medium | medium | factory |
| P5 | Lease-liveness reconciliation | medium | medium | factory |

Release: do not remove rc label — partial integration, recorded FAIL gate receipt.

APPROVE IMPROVEMENT prd-frankenkey-typing-intelligence-2026-09-27-s2-20261006-095211 P1,P2,P3,P4,P5
