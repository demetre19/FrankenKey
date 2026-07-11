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
- Preserve launcher/store icons unless the user explicitly requests icon changes; adaptive/circular foreground tweaks should target `ic_launcher_foreground` assets.
- Strings and settings keys are behavioral contracts; update tests when changing them.
- Typing settings expose separate millisecond repeat intervals for paste and destructive delete actions; both remain dependent on the global key-repeat toggle.
- Settings uses a dark platform Material activity theme by default without changing the keyboard theme preference or unrelated activities.
- Launcher welcome UI uses a dedicated dark theme; its Spacebar tutorial must mirror the live clean-layout clipboard, mode, emoji, and GIF corner labels and directions.
- Every user-visible Settings checkbox must have a concise, accurate summary; generated extra-key checkboxes use the shared extra-key summary.
- Adaptive-learning clear and individual word-forget copy must accurately describe the destructive scope and remain wired to explicit platform confirmation dialogs.
- Settings exposes default-on automatic update checks, in-app or GitHub delivery, current update status, and a manual check. Update notices must show release changelog text and require explicit acceptance or rejection.
- Snippet icon drawables are the curated Lucide set mapped by stable IDs in `SnippetIcons`; preserve their shared 24×24 stroke treatment and the packaged Lucide/Feather license notice.

## Work Guidance

- Match existing XML formatting and resource naming.
- Prefer small targeted resource changes; do not restyle unrelated layouts.
- Keep compact color chips for color selector UI per global preference.

## Verification

- For layout/settings behavior, run the focused tests that cover the changed resource, especially `SpacebarGestureLayoutTest`, `GifSearchKeyTest`, `SettingsUiContractsTest`, or `ClipboardParityTest`.
- For release-visible resources, run `assembleRelease` before delivery.

## Child DOX Index

- No child AGENTS.md files currently. Resource subfolders are owned here.
