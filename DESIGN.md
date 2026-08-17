---
name: FrankenKey
description: A private, capable, compact Android keyboard and Reader.
colors:
  quiet-graphite: "#0B0D10"
  graphite-surface: "#15191E"
  graphite-border: "#2A3139"
  paper-white: "#F4F7FA"
  quiet-slate: "#A8B2BE"
  mint-action: "#8CE9D7"
  mint-strong: "#55D6BE"
typography:
  headline:
    fontFamily: "sans-serif"
    fontSize: "20sp"
    fontWeight: 700
    lineHeight: 1.2
  title:
    fontFamily: "sans-serif"
    fontSize: "15sp"
    fontWeight: 700
    lineHeight: 1.25
  body:
    fontFamily: "sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: 1.4
  label:
    fontFamily: "sans-serif"
    fontSize: "13sp"
    fontWeight: 500
    lineHeight: 1.2
rounded:
  control: "8dp"
  surface: "10dp"
spacing:
  tight: "4dp"
  compact: "8dp"
  standard: "12dp"
  roomy: "16dp"
components:
  action-button:
    backgroundColor: "{colors.graphite-surface}"
    textColor: "{colors.paper-white}"
    rounded: "{rounded.control}"
    padding: "10dp 12dp"
    height: "48dp"
  icon-button:
    backgroundColor: "{colors.graphite-surface}"
    textColor: "{colors.paper-white}"
    rounded: "{rounded.control}"
    height: "48dp"
    width: "48dp"
  card:
    backgroundColor: "{colors.graphite-surface}"
    textColor: "{colors.paper-white}"
    rounded: "{rounded.surface}"
    padding: "12dp"
  text-field:
    backgroundColor: "{colors.graphite-surface}"
    textColor: "{colors.paper-white}"
    rounded: "{rounded.control}"
    padding: "12dp"
    height: "48dp"
---

# Design System: FrankenKey

## Overview

**Creative North Star: "The Quiet Instrument"**

FrankenKey is a focused tool: private, capable, and compact. Quiet graphite surfaces recede behind content while restrained mint identifies the current action or state. Interfaces should feel familiar immediately, with stable targets and no ornamental layers competing for limited phone space.

Every screen is designed small-screen first. Content scrolls independently above compact controls that remain available when the task needs them. The system explicitly rejects technical identifiers, tall single-column action stacks, persistent inactive controls, redundant actions, cramped navigation, and decorative complexity.

**Key Characteristics:**
- Compact without reducing 48dp touch targets.
- Flat, tonal surfaces with one restrained mint action color.
- Human labels instead of engine, locale, or storage identifiers.
- State-dependent controls that preserve the host app and reading space.
- Predictable equal-width grids and 8–12dp structural spacing.

## Colors

Quiet graphite and mint create a low-distraction, high-contrast tool palette.

### Primary
- **Mint Action**: Reserved for active playback, selected controls, progress, and the one dominant action in a region.
- **Mint Strong**: Used only when Mint Action needs a stronger boundary or pressed-state distinction.

### Neutral
- **Quiet Graphite**: The screen background; it should visually disappear behind content.
- **Graphite Surface**: Cards, fields, buttons, and the sticky Reader control dock.
- **Graphite Border**: Subtle separators and field boundaries where tonal contrast alone is insufficient.
- **Paper White**: Primary text and icon color.
- **Quiet Slate**: Secondary text, metadata, hints, and inactive descriptions.

**The One Mint Rule.** Mint is an operational signal, not decoration; never distribute it across unrelated text or surfaces.

**The Quiet Surface Rule.** Use tonal layering before borders and borders before shadows.

## Typography

**Display Font:** Android system sans-serif
**Body Font:** Android system sans-serif

**Character:** Neutral, legible, and native to Android. Typography communicates hierarchy through modest size and weight changes rather than dramatic scale.

### Hierarchy
- **Headline** (700, 20sp, 1.2): Screen titles such as Reader Library.
- **Title** (700, 15sp, 1.25): Card titles and compact section labels.
- **Body** (400, 14sp, 1.4): Explanations, metadata, and settings descriptions.
- **Label** (500, 13sp, 1.2): Buttons, progress values, compact controls, and status text.

**The Human Label Rule.** A user-facing voice or source name must lead with a friendly human label; technical identifiers are forbidden as primary text.

## Elevation

FrankenKey is flat by default and uses no decorative shadows. Depth comes from graphite tonal steps, spacing, and clear containment. The sticky Reader dock is separated from scrolling content by a subtle border or tonal boundary, never a floating-card shadow.

**The Flat Instrument Rule.** If a control looks detached or ornamental, remove the shadow and restore it to the layout grid.

## Components

### Buttons
- **Shape:** Gently compact corners (8dp) with a minimum 48dp target.
- **Primary:** Graphite Surface with Paper White text; Mint Action marks the active icon or selected state.
- **Focus / Pressed:** Preserve contrast and strengthen the mint or tonal boundary without changing geometry.
- **Grid actions:** Equal widths in two columns with an 8dp gutter; labels may wrap but targets remain aligned.

### Cards / Containers
- **Corner Style:** Quietly rounded (10dp).
- **Background:** Graphite Surface on Quiet Graphite.
- **Shadow Strategy:** None; use tonal separation.
- **Border:** Graphite Border only where the edge is otherwise ambiguous.
- **Internal Padding:** 12dp standard, 8dp for dense secondary rows.

### Inputs / Fields
- **Style:** Graphite Surface, Paper White text, Quiet Slate hint, 8dp corners, and 12dp internal padding.
- **Focus:** A restrained Mint Action boundary; never a glow.
- **Search:** Full width below the screen header, with a clear action and IME-aware bottom space.

### Navigation
- Icon-only navigation uses 48dp targets and descriptive accessibility labels. Keep at least 8dp of visual separation between a back control and the adjacent title. Reader titles center when they fit and activate single-line marquee only when measured overflow exists; Library and foreground-only Read Clipboard actions sit on a separate action row.

### Reader Control Dock
- The article/document area scrolls independently above a compact two-row bottom dock.
- Only previous/play-next/stop and speed remain sticky for arbitrarily long articles. When article text itself exceeds the viewport, a compact jump-to-bottom key remains with transport until it reaches the bottom settings. The keyboard always exposes Read Clipboard and Library, while active playback adds its conditionally scrolling title and previous/play-next/stop controls above them.
- Pitch, follow mode, voice, and network settings sit together after the article at the true bottom of scrolling content.
- The dock respects navigation and IME insets and must not cover highlighted reading text.

### Library Item
- URL articles show a bounded cached preview, source host/date, and safe Original action; retained inline images appear in article order without allowing decorative page chrome to consume the image bound.
- Documents or unavailable images use compact tonal fallbacks; broken images never reserve a blank oversized region.
- Search filters locally and does not transmit Library titles or content.

## Do's and Don'ts

### Do:
- **Do** keep every interactive target at least 48dp and structural gutters at 8–12dp.
- **Do** use equal-width compact action grids to preserve vertical space.
- **Do** keep Reader content scrollable above the sticky two-row transport and speed dock; place all other settings after the article.
- **Do** expose inactive Reader entry only for an empty editor, then replace it with a marquee title plus active transport and Library state.
- **Do** use human voice names, region flags, and small Offline or Online availability icons. Show Female or Male only when an exact documented system voice ID establishes it; otherwise omit gender.
- **Do** respect system and IME insets on narrow phones and with larger text.
- **Do** separate back controls and titles by at least 8dp.

### Don't:
- **Don't** expose technical engine or locale identifiers as primary labels.
- **Don't** use tall single-column action stacks that waste limited phone height.
- **Don't** show persistent controls for inactive features.
- **Don't** retain redundant buttons that compete with content or headings.
- **Don't** allow cramped navigation where icons and titles visually touch.
- **Don't** add decorative complexity that weakens readability or touch confidence.
- **Don't** use large color blocks, glows, gradients, or decorative shadows.
