# srcs/res DOX

## Purpose

- Own source-side drawable/resource generators.

## Ownership

- This folder owns generator inputs/helpers; top-level `res/` owns packaged output.

## Local Contracts

- Outputs must be reproducible; edit source/generator instead of generated runtime files.

## Work Guidance

- Keep generation simple and deterministic.

## Verification

- Run the relevant generator and inspect changed outputs.

## Child DOX Index

- None.
