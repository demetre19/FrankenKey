# srcs DOX

## Purpose

- Own source inputs that generate or compile into the FrankenKey Android app.
- Separate app Java source from layout/compose/font generation inputs.

## Ownership

- This folder owns `juloo.keyboard2/`, `layouts/`, `compose/`, `special_font/`, and source-side resource generation helpers.
- The source root owns Gradle/build wiring that consumes these files.

## Local Contracts

- Generated artifacts must remain reproducible from source inputs and scripts.
- Do not edit generated outputs when the source input should be changed instead.
- Keep upstream Unexpected Keyboard structure recognizable unless FrankenKey behavior requires divergence.

## Work Guidance

- Identify whether a file is app logic, generated data, or generator input before editing.
- Preserve license/credit files in layout and compose data.

## Verification

- For generator/input changes, run the relevant generator or focused Gradle/test command when available.
- For app code changes, use the `juloo.keyboard2` DOX verification.

## Child DOX Index

- `juloo.keyboard2/AGENTS.md` — main Java app package and feature modules.
- `layouts/AGENTS.md` — keyboard layout XML source inputs.
- `compose/AGENTS.md` — compose/dead-key/numpad JSON source data.
- `special_font/AGENTS.md` — special icon font SVG/source generation inputs.
- `res/AGENTS.md` — source-side drawable generation helpers.
