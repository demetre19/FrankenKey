# assets DOX

## Purpose

- Own packaged/source assets: language packs, icons, fonts, licenses, and the local 3D Reader surface.

## Ownership

- `assets/` owns sources; generated runtime copies belong to `res/`.

## Local Contracts

- Preserve source images unless explicitly replaced; avoid needless binary rewrites.
- Keep third-party notices in `assets/licenses/`, including Lucide ISC/MIT notice.
- Bundle matching `en_AU`, `en_GB`, and `en_US` Cdict (`dictionaries/`) and Hunspell plus generated 4,096-row short-word n-gram (`language_packs/`) assets with pinned sources, hashes, licenses, and notices.
- English Hunspell additions `gboard`, `omp`, and `cmux` require synchronized hashes/disclosure in `licenses/english-hunspell-source.txt`.
- `reader_3d.html` is the self-contained Private Drive mobile 3D Reader parity surface and owns its single inset-safe toolbar and settings sheet. Keep its controls/defaults exact, including an icon-only open-book action with an accessible Classic-reader label. The settings gear uses the direct immediate-button path so every tap opens the existing exact Reader settings sheet; do not replace or rearrange that modal. Enforce at least 60px top clearance in portrait and 50px right clearance in landscape for edge-to-edge Samsung system bars, keep the top inset theme-solid with decoration beginning below it, offset the landscape right gesture rail by the same clearance, and keep its 120px inward hit target easy to reach by thumb. Top chapter progress stacks above all Reader chrome; settings stack above gesture rails and generic help/confirmation modals above settings. Only background sliders may fade settings during live adjustment, and the sheet must remain 1% visible. While playing, preserve the selected decorative background and vignette through the reading area, shade only the upper and lower 24% stage bands, and hide toolbar controls and WPM while preserving word count and progress indicators. Do not show a Brainwave control. Keep its CSP offline-only and transfer articles through the bounded native bridge.
- Keep the Reader settings sheet in semantic, accessibly headed groups with alternating design-system graphite surfaces; every visible colour swatch must open its native colour picker and immediately reflect the selected value.
- The 3D AI control may only call the bounded native Reader AI bridge for an eligible URL article or readable EPUB. For EPUB it changes its accessible label to Book AI; the WebView keeps its network-disabled CSP and never receives API keys or raw OpenRouter request/response plumbing.

## Work Guidance

- Keep source icons distinct from generated launcher/resources.

## Verification

- Run focused dictionary/language-pack tests for typing data; verify image/font consumers after asset changes. For `reader_3d.html`, run JavaScript syntax plus focused Reader parity/security tests and verify the rendered Android surface.

## Child DOX Index

- None.
