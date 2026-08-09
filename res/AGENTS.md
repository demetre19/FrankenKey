# res DOX

## Purpose

- Own packaged Android resources.

## Ownership

- `res/` owns runtime resources; `srcs/` owns generator inputs.

## Local Contracts

- Keep clean/dense modes coherent. Clean mode hides Q/P/D/F/G/H/J/123/Backspace legends but preserves gestures; Space owns clipboard/mode/emoji/GIF corners; Enter owns voice.
- Clean/dense text modes reserve G for hidden four-direction cursor movement with a faint availability border; other letter swipes retain global teach/forget behavior. Numeric/symbol middle rows retain gradual word-delete.
- `…` opens saved-order Extra Keys: one row, up to three expanded scrollable rows, and `+` manager for visibility/order/custom modifier combinations.
- Launcher guidance teaches G-only four-direction cursor movement, global teach/forget swipes, and Shift-swipe selection before Delete; held-Delete is secondary.
- Bundle matching AU/UK/US subtype labels/dictionaries. Preserve launcher icons unless explicitly changed.
- Strings/settings keys are contracts. Paste/delete repeats are separate and depend on global repeat.
- Settings/launcher use dedicated dark Material surfaces, restrained spacing, accurate summaries, `Private by design` first, live Spacebar guidance, and a top-level title/summary search.
- Grammar/voice default off and disclose Android service boundaries. Adaptive-learning copy states that vocabulary comes only from explicit teaching or backspace/edit followed by selecting the intended suggestion; ordinary typing, automatic autocorrection, and consecutive words never teach. The Learned Words page defaults to Taught, keeps Taught and Adaptive distinct, stays searchable/alphabetical, offers All/1–9/10+ length filters, and confirms per-word deletion. Destructive actions require confirmation.
- Updates default on, expose status/manual checks, show changelogs, and require accept/reject.
- Snippet icons remain the licensed curated Lucide 24×24 set.
- Mobile UI is compact: ≥48dp targets (documented compact text actions excepted), ≥8dp visible separation, accessible icon labels, and small square color chips. Text inside every bordered input surface and learned-word row stays at least 12dp from the inner left/right border; encode that inset on the input or its content container and preserve it with layout contracts.
- Full/minimal Reader transport keeps previous/play-next/stop plus a full-width speed slider. Full Reader moves pitch/follow/voice/network settings after content, supports themed contrast/follow scrolling, and conditionally shows jump-to-bottom.
- Reader source actions order Original, Read Clipboard, Library; text actions are content-sized 36dp surfaces with 20dp horizontal padding and ≥8dp separation. Article text uses 12dp horizontal insets.
- Reader images fill the column with aspect ratio and open private zoom/pan viewer.
- Keyboard Reader is explicit default-off. When enabled, empty readable editors show compact Read Clipboard/Library plus a direct Settings cog; real input/candidates hide them. Explicit playback replaces candidates with title, transport, and speed.
- Voice rows use human names, AU/US/UK flags, truthful offline/network status, and gender only for documented exact voice IDs.

## Work Guidance

- Match resource naming/style; make targeted changes only.

## Verification

- Run focused layout/settings tests; run `assembleRelease` for release-visible resources.

## Child DOX Index

- None.
