# vendor DOX

## Purpose

- Own vendored third-party code and data such as Hunspell and cdict.

## Ownership

- This folder owns third-party source snapshots and local integration notes.
- App wrappers/integration code live in main source packages.

## Local Contracts

- Preserve upstream license files and attribution.
- Avoid modifying vendored code unless necessary for build or integration; prefer wrapper fixes outside vendor when possible.
- Local Cdict patch: format version 1 is preserved while construction/traversal bounds, Unicode word materialization, result-owner validation, fixed-workspace spatial search, Java/JNI DTOs, and deterministic status/results are maintained locally; the obsolete byte-distance API is intentionally removed.

## Work Guidance

- Keep vendor changes surgical and isolated.
- Do not run broad dependency upgrades or replace vendor snapshots casually.

## Verification

- For Cdict changes, build Android native libraries for all release ABIs and run the focused Java query/decoder tests; run the vendored Dune/Cram harness when `dune`/OCaml tooling is available.

## Child DOX Index

- No child AGENTS.md files currently. Vendored subtrees are owned here unless they grow local workflows.
