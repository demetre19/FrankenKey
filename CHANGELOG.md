# FrankenKey changelog

This file summarizes the 10 most recent major user-visible changes. For downloadable builds and complete release notes, see [GitHub Releases](https://github.com/demetre19/FrankenKey/releases).

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

## 2.0.82 — version code 133

- Added comfortable light and dark Reader modes, automatic follow-along scrolling, clearer spacing, compact source actions, and current-mode icons.
- Added an Extra Keys panel with holdable modifiers, navigation and function keys, and common shortcuts behind a visible keyboard button.
- Fixed a crash when opening Reader Library and improved separation between adaptive-learning cards.

## 2.0.79 - version code 130

- Added the optional, default-off Reader for private text-to-speech from clipboard text, prose fields, imported files, and saved public articles.
- Added persistent playback, follow-along highlighting, tap-to-seek, speeds up to 800 words per minute, clear voice availability, and a searchable Library.
- Improved article extraction so reading starts with the article, lazy-loaded images are retained in order, and large images fill the reading column.
- Added a full-screen article image viewer with pinch zoom, panning, and double-tap reset.




