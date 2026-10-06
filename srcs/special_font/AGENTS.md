# special_font DOX

## Purpose

- Own special-font SVG/icon glyph sources and build inputs.

## Ownership

- This folder owns sources/instructions; destination subtrees own packaged outputs.

## Local Contracts

- Preserve glyph IDs and intent unless deliberately redesigning an icon.
- `013.svg`/`014.svg` (layout-switch forward/back) intentionally share the plain keyboard glyph `009.svg`; the packaged `assets/special_font.ttf` cmap maps E013/E014 to the E009 glyph, so a fontforge rebuild preserves the same icon.
- Never bulk-normalize SVGs without need.

## Work Guidance

- Isolate edits to intended glyphs.

## Verification

- Run available font generation and visually inspect affected keyboard icons.

## Child DOX Index

- None.
