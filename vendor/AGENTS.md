# vendor DOX

## Purpose

- Own vendored third-party code/data, including Hunspell and Cdict.

## Ownership

- `vendor/` owns snapshots/integration notes; app wrappers belong to source packages.

## Local Contracts

- Preserve upstream licenses and attribution.
- Prefer wrapper fixes; change vendor code only when build/integration requires it.
- Preserve Cdict format v1 plus local bounds, Unicode materialization, owner validation, fixed-workspace spatial search, Java/JNI DTOs, deterministic results/status, and removal of the obsolete byte-distance API.

## Work Guidance

- Keep changes isolated; no casual snapshot replacement or broad upgrades.

## Verification

- For Cdict: build all release ABIs, run focused Java query/decoder tests, and run Dune/Cram when OCaml tooling exists.

## Child DOX Index

- None.
