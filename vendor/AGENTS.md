# vendor DOX

## Purpose

- Own vendored third-party code/data, including Hunspell and Cdict.

## Ownership

- `vendor/` owns snapshots/integration notes; app wrappers belong to source packages.

## Local Contracts

- Preserve upstream licenses and attribution.
- Prefer wrapper fixes; change vendor code only when build/integration requires it.
- Preserve Cdict format v1 plus local bounds, Unicode materialization, owner validation, fixed-workspace spatial search, Java/JNI DTOs, deterministic results/status, and removal of the obsolete byte-distance API.

- `ocaml-cdict/builder/k_medians.ml` clamps clusters to `k - 1`. Upstream wraps the top frequency cluster to 0 in 4-bit packing. The bundled `.dict` files stay upstream byte copies and are repaired at runtime by the pack frequency tables.

## Work Guidance

- Keep changes isolated; no casual snapshot replacement or broad upgrades.

## Verification

- For Cdict: build all release ABIs, run focused Java query/decoder tests, and run Dune/Cram when OCaml tooling exists.

## Child DOX Index

- None.
