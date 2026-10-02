# Typing Intelligence PRD DOX

## Purpose

- Own the 2026-09-27 Typing Intelligence program: the overview/philosophy, five parallel lane handoff PRDs (A strip reliability, B email memory, C adaptive learning, D power typing, E grammar), the launch-facing section PRDs at the delivery root (`../../PRD-FrankenKey-TI-*.md`), the Stage 2.5 evidence artifact (`RESEARCH-typing-intelligence-2026-09-27.md`), and the executable run plan (`crew-plan-typing-intelligence-2026-09-27.crew.json` + `.md`).

## Ownership

- `00-Overview-and-Philosophy.md` owns principles, recorded user decisions, open questions, the lane dependency map, the integration protocol, and shared cross-lane contracts (settings prefixes, storage, candidate roles, editor eligibility).
- Each `A`–`E` file owns its lane's scope, requirements, stages, tests, DOX updates, and changelog copy.
- `RESEARCH-typing-intelligence-2026-09-27.md` owns the verified-anchor evidence, prior art, alternatives, assumptions, and premortem for the run plan.
- `crew-plan-typing-intelligence-2026-09-27.crew.json` owns the executable lane DAG (8 machinery lanes), shared_paths justifications, gates, and context/evidence bindings; the `.md` companion records why it is one run, the adopted Q1–Q5 defaults, and the no-promote launch contract.
- Android source (`../FrankenKey-autobuild-autocorrect/`) owns the implementation. These documents never override source DOX until a lane lands its same-change contract updates.

- Principles in the overview bind every lane. A lane conflict with them is raised, not shipped around.
- Hard dependencies are fixed: A0 before any new candidate role/acceptance; C1 before D1b and D3. Everything else may run in parallel. The crew plan encodes the same order through lane `depends_on` edges (a0-seams first; c-strip waits on a-strip+c-learn; d-power-gestures waits on a-strip+c-learn).
- Launched 2026-09-28 as one Crew run on `autobuild/frankenkey-autocorrect-suggestions` @ `57f1356`, queued without `promote`: lane merges onto the run integration branch are automatic, but landing on the source branch, version bumps, signed/canonical APK builds, phone upload, tag, and release each still need separate operator approval (root Release Contracts).

- Principles in the overview bind every lane. A lane conflict with them is raised, not shipped around.
- Hard dependencies are fixed: A0 before any new candidate role/acceptance; C1 before D1b and D3. Everything else may run in parallel.
- Only the integrator bumps the version, builds the signed canonical APK, and uploads it to the phone (root Release Contracts).
- Optional learning behaviors (C 1–6) must each keep a Settings explanation, and with them OFF the 2.0.119 behavior is kept.

## Work Guidance

- Record answered open questions and scope changes in the overview's decisions table, then update the affected lane files in the same change.
- Keep requirement IDs (`A-F1`, `B-F3`, …) and stage IDs (`A0`, `C1`, …) stable; lane reports and tests reference them.

## Verification

- At each wave exit, check that lane PRDs, the overview lane map, the crew plan DAG, and the source DOX lines listed in overview §4 agree.
- Launch health is read from `crew queue-inspect --slug crew-plan-typing-intelligence-2026-09-27` and `crew_report.py status <run_dir>`; a pending worker-pool hold means queue capacity, not a defect.

## Child DOX Index

- None.
