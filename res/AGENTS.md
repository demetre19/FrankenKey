# res DOX

## Purpose

- Own packaged Android resources: layouts, XML keyboard definitions, values, drawables, raw media, icons, menus, and localized strings.

## Ownership

- This folder owns runtime resources under `res/`.
- `srcs/` owns source inputs that generate resources when applicable.

## Local Contracts

- UI/resource changes must keep clean Fleksy mode and FrankenKey mode coherent.
- Clean Fleksy mode keeps the Q, P, D, F, G, H, J, 123, and Backspace secondary gesture actions available but hides their compact legends to preserve an uncluttered everyday layout.
- In clean and dense text layouts, the spacebar owns clipboard at top-left, emoji at bottom-left, and GIF at bottom-right; voice typing remains on the far-right Enter/Done key and must not move back to the spacebar.
- Clean and dense text-layout middle rows are the cursor touchpad origin: hidden west/east/north/south sliders provide left/right/up/down movement without rendering arrow clutter. Do not move this behavior onto the already-tooled spacebar. Numeric and symbol middle rows retain their hidden gradual word-delete gesture.
- Clean and dense text layouts expose a visible `…` Extra Keys trigger without displacing the spacebar's authored gesture tools. The default panel is one horizontally swipeable saved-order row; its own second `…` toggles at most three horizontally scrollable rows. `+` opens the Extra Keys Bar manager for per-shortcut visibility, drag reordering, and custom Ctrl/Alt/Shift/Cmd combinations.
- Launcher navigation guidance must name G and the other middle-row letters for four-direction cursor movement, then describe Shift-modified swipe selection followed by Delete; do not present the held-Delete drag as the primary editing workflow.
- English subtype resources must expose Australian (`en_AU`), UK (`en_GB`), and US (`en_US`) choices with matching dictionary IDs and region labels.
- Preserve launcher/store icons unless the user explicitly requests icon changes; adaptive/circular foreground tweaks should target `ic_launcher_foreground` assets.
- Strings and settings keys are behavioral contracts; update tests when changing them.
- Typing settings expose separate millisecond repeat intervals for paste and destructive delete actions; both remain dependent on the global key-repeat toggle.
- Settings uses a dark platform Material activity theme by default without changing the keyboard theme preference or unrelated activities. Rows use platform switches, one consistent inset surface per theme, and restrained category spacing rather than alternating section colors.
- Launcher welcome UI uses a dedicated dark theme; its Spacebar tutorial must mirror the live clean-layout clipboard, mode, emoji, and GIF corner labels and directions.
- The launcher/settings home screen presents the `Private by design` card as its first content block, ahead of introductory copy and setup controls.
- Every user-visible Settings switch must have a concise, accurate summary; generated extra-key checkboxes use the shared extra-key summary.
- System grammar and multimodal voice settings default off. Their summaries/disclosures must identify the Android service boundary; the compact assistant strip may replace candidates temporarily but must leave the keyboard layout visible.
- Adaptive-learning status and clear/forget copy must accurately describe learned words, correction patterns, and touch calibration; destructive actions remain wired to explicit platform confirmation dialogs.
- Settings exposes default-on automatic update checks, in-app or GitHub delivery, current update status, and a manual check. Update notices must show release changelog text and require explicit acceptance or rejection.
- Snippet icon drawables are the curated Lucide set mapped by stable IDs in `SnippetIcons`; preserve their shared 24×24 stroke treatment and the packaged Lucide/Feather license notice.
- Mobile screens must preserve vertical space with compact spacing, minimal branding and headings, and familiar icon-only actions where the symbol is unambiguous. Keep visible text only when it is needed for clarity. Icon actions retain descriptive accessibility labels and at least a 48dp touch target; explicit compact text-action contracts may use their documented smaller height.
- Keep at least 8dp of visible separation between every button surface and every adjacent button or other UI element. A compact button may use a smaller documented visual height, but it must not touch neighboring controls or content. The Extra Keys strip uses 4dp top/bottom edge margins per button, which combine to 8dp between its expanded rows.
- The full Reader and the keyboard/clipboard minimal playback strip keep previous/play-next/stop and a full-width thumb-friendly speed slider directly below transport controls. Pitch, follow, voice, and network controls belong in the full Reader scrolling settings card after article content. The full Reader balanced header centers titles that fit and exposes a persistent 48dp current-state Light/Dark icon toggle; all Reader text, icon controls, settings surfaces, and highlight colors must follow the selected theme with readable contrast. Follow-along scrolls the outer article page to keep the spoken passage in a stable middle reading band. A jump-to-bottom control appears only while the article text itself exceeds the viewport and more content remains below.
- Full Reader source actions place Original before Read Clipboard and Library. Source and Library-row text actions are content-sized, 36dp high, use 20dp horizontal padding and the shared 12dp rounded themed surface, and keep at least 8dp from adjacent controls or content. Article text keeps 12dp horizontal insets, and the sticky playback dock keeps explicit top separation from article content.
- Large retained Reader article images fill the reading column while preserving aspect ratio. Tapping one opens the private full-screen zoom-and-pan viewer with accessible Back/close handling and concise gesture guidance.
- The keyboard Reader and text-to-speech surface is an explicit, default-off Settings opt-in. When disabled, neither the normal keyboard nor clipboard pane shows Reader actions or playback controls. When enabled, one compact Read Clipboard and Library row appears only for an empty readable editor and disappears immediately when typing, composing, or candidates begin. Source actions appear only without real candidates; the source row and explicitly expanded playback transport replace the candidates strip so no empty bar remains above Reader controls. Source buttons are 36dp high with 4dp internal vertical padding, 16dp horizontal padding, an 8dp gap, 8dp source-strip padding above and below, and themed key colors, label colors, ripple, and the same small corner radius as keyboard keys. Expanded playback replaces that row with a centered fitting title or overflow-only marquee, an 8dp title-to-controls gap, and previous/play-next/stop controls.
- Reader voice rows use compact AU/US/UK flags, human names, explicit Available offline or Uses network text, and small status icons under conditional group headings. Exact `-network` counterparts reuse the matching named local voice family's Female or Male presentation; unknown voice families use a truthful regional label without guessed gender.

## Work Guidance

- Match existing XML formatting and resource naming.
- Prefer small targeted resource changes; do not restyle unrelated layouts.
- Keep compact color chips for color selector UI per global preference.

## Verification

- For layout/settings behavior, run the focused tests that cover the changed resource, especially `SpacebarGestureLayoutTest`, `GifSearchKeyTest`, `SettingsUiContractsTest`, or `ClipboardParityTest`.
- For release-visible resources, run `assembleRelease` before delivery.

## Child DOX Index

- No child AGENTS.md files currently. Resource subfolders are owned here.
