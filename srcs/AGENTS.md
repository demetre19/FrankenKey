# srcs DOX

## Purpose

- Own source inputs compiled/generated into FrankenKey.

## Ownership

- Own `juloo.keyboard2/`, `layouts/`, `compose/`, `special_font/`, and `res/`; root owns build wiring.

## Local Contracts

- Generated artifacts must be reproducible; edit their source inputs.
- Keep upstream structure recognizable unless FrankenKey behavior requires divergence.

## Work Guidance

- Identify app logic vs generator input before editing; preserve licenses/credits.

## Verification

- Run relevant generators for inputs; follow `juloo.keyboard2/` checks for app code.

## Child DOX Index

- `juloo.keyboard2/AGENTS.md` — app logic/features.
- `layouts/AGENTS.md` — keyboard layout inputs.
- `compose/AGENTS.md` — compose/key JSON.
- `special_font/AGENTS.md` — icon-font sources.
- `res/AGENTS.md` — resource generators.
