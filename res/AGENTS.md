# res DOX

## Purpose

- Own packaged Android resources.

## Ownership

- `res/` owns runtime resources; `srcs/` owns generator inputs.

## Local Contracts

- Keep clean/dense modes coherent. Clean mode hides Q/P/D/F/G/H/J/123/Backspace legends but preserves gestures; Space owns clipboard/mode/emoji/GIF corners; Enter's northwest corner owns voice while bare Enter owns the editor action.
- Clean/dense text modes reserve G for hidden four-direction cursor movement with a faint availability border; other letter swipes retain global teach/forget behavior. Numeric/symbol middle rows retain gradual word-delete.
- `…` opens saved-order Extra Keys: one row, up to three expanded scrollable rows, and `+` manager for visibility/order/custom modifier combinations.
- Launcher guidance teaches G-only four-direction cursor movement, Shift+G selection, Z/X/C/V corner editing, Backspace word/progressive deletion, global teach/forget swipes, Enter/Shift+Enter behavior, Spacebar corners, and four-exact-correction recall through a compact modal below the Spacebar guide.
- Bundle matching AU/UK/US subtype labels/dictionaries. Preserve launcher icons unless explicitly changed.
- Strings/settings keys are contracts. Paste/delete repeats are separate and depend on global repeat.
- Settings/launcher use dedicated dark Material surfaces, restrained spacing, accurate summaries, `Private by design` first, live Spacebar guidance, and a top-level title/summary search.
- Grammar/voice default off and disclose Android service boundaries. Adaptive-learning copy states that nothing becomes a learned word silently: Teach gestures ask first; the third exact unknown literal in one editor session asks whether to keep or replace it; automatic autocorrection never teaches vocabulary. The Learned Words page defaults to Taught, keeps Taught and Corrections distinct, shows editable source-to-target or source-to-Best suggestion rows, stays searchable/alphabetical, offers All/1–9/10+ source-length filters, and confirms per-row deletion. Destructive actions require confirmation.
- Updates default on, expose status/manual checks, show changelogs, and require accept/reject.
- Snippet icons remain the licensed curated Lucide 24×24 set.
- Mobile UI is compact: ≥48dp targets (documented compact text actions excepted), ≥8dp visible separation, accessible icon labels, and small square color chips. Text inside every bordered input surface and learned-word row stays at least 12dp from the inner left/right border; encode that inset on the input or its content container and preserve it with layout contracts.
- Full/minimal Reader transport keeps previous/play-next/stop plus a full-width speed slider. Full Reader moves pitch/follow/voice/network settings after content, supports themed contrast/follow scrolling, and conditionally shows jump-to-bottom.
- Reader source actions order Original, Read Clipboard, Library; text actions are content-sized 36dp surfaces with 20dp horizontal padding and ≥8dp separation. Article text uses 12dp horizontal insets.
- Reader AI appears only for non-empty safe URL articles, text explicitly loaded with Read Clipboard, or a safely parsed EPUB. Its navigation-inset-safe native workspace uses FrankenKey Reader colors, readable bordered inputs, 12sp labels, 42dp actions with ≥8dp separation, an accessible saved-items bookmark icon, and an accessible red close icon. Saved results expose compact source/output filters and preserve Copy/Share/Favorite/Delete, available-source reopen, and non-Quiz Speed Read.
- Reader Library defaults to Books, keeps Articles &amp; Text in a separate tab, caps the compact cover grid at two portrait-phone or three wide/landscape columns, uses restrained graphite cards/fallback covers, and exposes ≥48dp favorite/menu controls plus compact horizontally scrollable collection chips. Selected Books and filter states use the Reader mint accent with graphite text, not platform blue.
- Classic EPUB uses a compact Private Drive-style title/author/progress header, continuous reading canvas, and one fixed 52dp bottom row containing `A-`, `A+`, `Aa`, outline image, outline sun, `AI`, and `3D` in that order. Compact 17sp text marks and 18dp artwork keep evenly weighted ≥48dp targets, accessible labels, borderless pressed feedback, and dark/sepia/light contrast across the native header, controls, system bars, and content. Selected controls use Reader mint rather than platform blue.
- Reader images fill the column with aspect ratio and open private zoom/pan viewer.
- Keyboard Reader is explicit default-off. Empty readable editors show Settings, Read Clipboard, Library, Insert Image, and Voice; Insert Image uses a bounded private FrankenKey cache for compatible rich-content editors and gives unsupported apps an honest attachment-button fallback. Real input/candidates hide the shortcuts. Explicit playback replaces candidates with title, transport, and speed.
- Android backup keeps Reader library metadata, collections, progress, EPUB appearance, non-secret AI prompts/settings, and saved AI outputs eligible. It excludes the encrypted OpenRouter key, active playback text, disposable AI response/evidence/job caches, and derived Reader covers; user-owned EPUBs and transient parsed content never enter app backup domains.
- Voice rows use human names, AU/US/UK flags, truthful offline/network status, and gender only for documented exact voice IDs.

## Work Guidance

- Match resource naming/style; make targeted changes only.

## Verification

- Run focused layout/settings tests; run `assembleRelease` for release-visible resources.

## Child DOX Index

- None.
