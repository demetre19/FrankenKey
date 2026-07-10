# srcs/res DOX

## Purpose

- Own helper sources for generating resource drawables from source assets.

## Ownership

- This folder owns source-side resource generators such as SVG-to-vector helpers.
- Runtime packaged resources live under top-level `res/`.

## Local Contracts

- Generated drawable output must remain reproducible from these inputs.
- Do not edit generated runtime resources when the generator/source should change.

## Work Guidance

- Keep generator behavior simple and deterministic.

## Verification

- Run the relevant generator and inspect changed outputs when modifying this folder.

## Child DOX Index

- No child AGENTS.md files currently. Source resource helpers are owned here.
