# Typing Intelligence PRD DOX

## Purpose

- Own the 2026-09-27 Typing Intelligence program: the overview/philosophy and five parallel lane handoff PRDs (A strip reliability, B email memory, C adaptive learning, D power typing, E grammar).

## Ownership

- `00-Overview-and-Philosophy.md` owns principles, recorded user decisions, open questions, the lane dependency map, the integration protocol, and shared cross-lane contracts (settings prefixes, storage, candidate roles, editor eligibility).
- Each `A`–`E` file owns its lane's scope, requirements, stages, tests, DOX updates, and changelog copy.
- Android source (`../FrankenKey-autobuild-autocorrect/`) owns the implementation. These documents never override source DOX until a lane lands its same-change contract updates.

## Local Contracts

- Principles in the overview bind every lane. A lane conflict with them is raised, not shipped around.
- Hard dependencies are fixed: A0 before any new candidate role/acceptance; C1 before D1b and D3. Everything else may run in parallel.
- Only the integrator bumps the version, builds the signed canonical APK, and uploads it to the phone (root Release Contracts).
- Optional learning behaviors (C 1–6) must each keep a Settings explanation, and with them OFF the 2.0.119 behavior is kept.

## Work Guidance

- Record answered open questions and scope changes in the overview's decisions table, then update the affected lane files in the same change.
- Keep requirement IDs (`A-F1`, `B-F3`, …) and stage IDs (`A0`, `C1`, …) stable; lane reports and tests reference them.

## Verification

- At each wave exit, check that lane PRDs, the overview lane map, and the source DOX lines listed in overview §4 agree.

## Child DOX Index

- None.
