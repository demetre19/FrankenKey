# scripts DOX

## Purpose

- Own helper scripts used to generate or transform FrankenKey source/resource data.

## Ownership

- This folder owns script implementation.
- Generated outputs are owned by their destination subtree.

## Local Contracts

- Scripts must be deterministic for the same inputs.
- Prefer Python standard library and existing project patterns.
- Do not add dependencies without reviewing supply-chain impact.

## Work Guidance

- Keep scripts simple and runnable from the project root unless documented otherwise.
- Update consuming docs/contracts when script behavior changes generated artifacts.

## Verification

- Run the script on a minimal/current input when changed, or explain why no safe invocation exists.

## Child DOX Index

- No child AGENTS.md files currently. Scripts are owned here.
