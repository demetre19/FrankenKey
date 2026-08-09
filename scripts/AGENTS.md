# scripts DOX

## Purpose

- Own source/resource generation and transformation scripts.

## Ownership

- `scripts/` owns implementations; destination subtrees own generated outputs.

## Local Contracts

- Same inputs must produce deterministic outputs.
- Prefer Python stdlib/project patterns; review supply-chain impact before adding dependencies.
- Behavior changes must update consumer contracts/docs.

## Work Guidance

- Keep scripts simple and root-runnable unless documented otherwise.

## Verification

- Run the changed script on minimal/current input, or state why no safe invocation exists.

## Child DOX Index

- None.
