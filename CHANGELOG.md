# FrankenKey changelog

This file summarizes the 10 most recent major user-visible changes. For downloadable builds and complete release notes, see [GitHub Releases](https://github.com/demetre19/FrankenKey/releases).


## 2.0.118 — version code 169

- Added a floating omnibutton: a draggable overlay bubble hosted by the accessibility service that opens Reader AI over any app, even with the keyboard hidden. Tap for the tap action or swipe in a direction for that action; hold ~half a second then move to drag. It hides while the keyboard is up or a password field is on screen.
- Added SpeedyWatch-style floating-button styling in Settings > Omnibutton: enable toggle, opacity slider with a live preview and percentage readout, round/square shape, and button + icon colors via swatches or `#AARRGGBB` hex.
- Made the floating button dispatch the same configurable tap and eight-direction swipe actions as the keyboard omnibutton, reading the shared settings.
- Fixed page capture to grab the live window on demand before Reader AI opens, skipping FrankenKey's own windows, so summaries and chat get real page text instead of a stale URL-bar snapshot.
- Fixed a Reader AI crash when asking a question before a model was loaded by re-resolving the model instead of dereferencing a cleared selection.
- Reordered the keyboard so the snippet row no longer jumps when the Reader strip appears, made all Reader strip buttons a uniform height, and moved the omnibutton to the right edge with voice on the opposite end.
- Renamed the keyboard shortcut map entry to Omnibutton with a short explanation, added an AI section, and gave the Omnibutton settings row its icon.

## 2.0.107 — version code 158

- Moved every key already provided by the `…` Extra Keys strip (Ctrl, Alt, Meta, Esc, Tab, arrows, Home/End/Page Up/Page Down, Insert, Delete, F1–F12) off the visible typing surface; they stay in the strip and as hidden swipe gestures for SSH and terminal use.
- Made the bottom-left key a plain `123` numeric switch and gave the Spacebar's top corners a keyboard icon for switching Everyday and Coding modes.
- Made snippet pages wrap around when swiping past the first or last page, and added a Keyboard setting that can hide the dedicated speak key on the clean number and symbol pages.
- Fixed the keyboard Reader strip (Settings, Read Clipboard, Library, Voice) so it appears reliably on empty editors instead of waiting for the first tap or selection change.
- Removed Attach Image, which only worked in apps that declare image support.

## 2.0.106 — version code 157

- Made every keyboard Teach gesture ask before saving a word, and added a session-only review after the third exact use of one unknown literal without persisting passive typing history.
- Added **Learned words > Corrections**, with searchable and editable `source → replacement` or `source → Best suggestion` rows, confirmed deletion, and direct conversion from an accidentally taught word.
- Made explicit replacement rules deterministic even when the mistyped pattern and intended word are unrelated; user-authored rules now reserve their candidate ahead of bounded heuristic providers.
- Updated Settings, launcher guidance, privacy copy, and the public README to explain the new review and correction-management workflow.

## 2.0.102 — version code 153

- Rebuilt the Classic EPUB toolbar in a clean compact style, with smaller `A-`, `A+`, `Aa`, image, theme, `AI`, and `3D` controls and the greener FrankenKey Reader mint.
- Fixed the 3D Reader settings gear so it reliably opens the existing grouped settings sheet without changing that modal's layout.
- Made EPUB summaries cover every readable chapter in source order and reject obsolete whole-book results that stopped early.
- Made book quizzes resilient: an incomplete chapter is displayed and cached, later chapters continue, and running Quiz again reuses completed chapters while requesting only missing questions.
- Expanded the README with step-by-step Reader AI setup, two-summary, per-chapter quiz, grounded chat, saved-results, privacy, and Speed Read guidance.

## 2.0.101 — version code 152

- Replaced the keyboard Gallery launcher with Attach Image, which opens Android's image picker and sends the selected image to compatible rich-content editors.
- Centered the keyboard Reader action group with equal spacing and horizontal scrolling so Settings, Reader, Library, Attach Image, and Voice remain reachable on narrow screens and with larger text.
- Added a default-on Typing assistance option that turns two consecutive spaces in prose into a full stop followed by one space; structured fields and terminals keep literal spacing.
- Fixed the candidate-strip Teach action so deliberate teaching reaches Learned Words in safe prose editors even when the host disables passive personalization; structured fields, terminals, and unsafe editors remain excluded.
- Matched the 3D Reader to Private Drive mobile with one compact toolbar, no duplicate title or Brainwave control, full-edge solid system-bar coverage, top progress above Reader chrome, playback edge shading that preserves the selected background and vignette, a 50px Samsung-safe gutter, a wide thumb-friendly word-distance gesture zone, correctly stacked settings/modals, and a 1%-visible live background preview.
- Added a configurable 2D/3D RSVP Reader with stereo word spacing, adaptive word grouping, focus-letter guides, crosshairs, chapter cues, bookmarks, typography and colour controls, reading-rhythm pauses, backgrounds, vignette depth, and touch gestures for speed, spacing, font size, navigation, and plain-text view.
- Added opt-in Reader AI for safe public articles and explicitly loaded clipboard text, with two configurable summaries, chat, quizzes, OpenRouter model and prompt controls, encrypted local key storage, and explicit disclosure before text is sent.
- Added a searchable, sortable, favourite-aware, date-grouped saved Reader AI library with copy, share, delete, and read-aloud actions.
- Reorganized 3D Reader settings into accessible semantic groups with working colour pickers and responsive columns that keep labels, sliders, selectors, and values inside narrow-phone cards.
- Documented the G key's mouse-like four-direction cursor control, distance-based acceleration, reversible movement, and Shift-selection gestures.
- Added official-source test-install guidance for Android and Samsung, including scoped unknown-source permission, temporary Auto Blocker handling, Play Protect safeguards, test-build update prompts, checksum verification, and immediate security-setting restoration.


## 2.0.100 — version code 151

- Fixed the keyboard Gallery shortcut so it opens the Gallery app's Albums screen instead of an empty or single-image view.


## 2.0.99 — version code 150

- Added compact Gallery and Voice shortcuts to the keyboard Reader controls, with Settings at the far left and Voice at the far right.
- Preserved the existing Read Clipboard, Library, and Done-key voice gesture controls.


## 2.0.98 — version code 149

- Kept Learned Words teaching, search, and vocabulary-row text visibly inset on first load and when rows are recycled.
- Rebuilt the portrait PIN and numeric keyboards as cleaner five-unit grids with larger number keys and only essential visible controls.


## 2.0.97 — version code 148

- Limited Adaptive vocabulary to explicit teaching and deliberate correction choices made after editing a word.
- Added a one-time cleanup that removes passive-only words and old word-pair memory while preserving Taught words, correction-backed targets, and touch calibration.
- Added a second three-word Suggestions page: swipe left to bring ranks 4–6 in from the right, then swipe right to return.
- Added a Keyboard setting that hides the dedicated full-stop key and gives its width back to the spacebar.
- Kept Settings search, Learned Words fields, and learned-word rows visibly inset from bordered edges.

## 2.0.93 — version code 144

- Combined touch-aware decoding with compact conversational bigram and trigram evidence for short-word corrections.
- Preserved explicitly taught casing and deliberate lowercase sentence starts when Shift is manually turned off.
- Extended safe delayed correction across recent text, including accidental unspaced periods, while protecting URLs and real sentence endings.
- Limited optional system grammar checks to completed sentence punctuation and excluded private, structured, and terminal fields.

## 2.0.88 — version code 139

- Added a full-width reading-speed slider directly below the keyboard and clipboard Reader playback controls.
- Made speed changes immediately available from the minimal playback view while keeping the current WPM visible.

## 2.0.87 — version code 138

- Made the Extra Keys bar configurable: every shortcut can be shown or hidden and dragged into a preferred order.
- Added custom `Ctrl`, `Alt`, `Shift`, and `Cmd` combinations with supported keys and keyboard commands.
- Limited the expanded Extra Keys panel to three horizontally scrollable rows.

