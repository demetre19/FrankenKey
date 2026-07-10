# special_font DOX

## Purpose

- Own SVG/icon glyph sources and build inputs for FrankenKey's special font.

## Ownership

- This folder owns glyph source files and font build instructions.
- Packaged font/resource outputs are owned by their destination subtree.

## Local Contracts

- Preserve glyph identifiers and visual intent unless deliberately redesigning a key icon.
- Do not bulk-normalize SVGs without need.

## Work Guidance

- Keep glyph edits isolated to the intended icon.
- Verify rendered key/icon appearance when changing glyphs.

## Verification

- Run the font build/generation path when glyph sources change, if available.
- Visually inspect affected icons in the keyboard when practical.

## Child DOX Index

- No child AGENTS.md files currently. Font source assets are owned here.
