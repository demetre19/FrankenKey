# FrankenKey changelog

This file summarizes the 10 most recent major user-visible changes. For downloadable builds and complete release notes, see [GitHub Releases](https://github.com/demetre19/FrankenKey/releases).

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

## 2.0.65 — version code 116

- Fixed the incorrect dictionary-install message and made English (Australia), English (United Kingdom), and English (United States) consistently available from local bundled data.
- Updated the launcher guide for four-direction cursor movement from G or any middle-row letter and Shift-swipe selection followed by Delete.

## 2.0.63 — version code 114

- Restored the held-Delete drag so it deletes the highlighted range instead of leaving the selection active.
- Hold Delete to remove letters, continue left to highlight words and complete sentences, reverse to shrink the range, and lift to delete it.

## 2.0.61 — version code 112

- Added accelerated four-direction cursor movement from `G` and every middle-row letter.
- Hold Shift while dragging a middle-row letter to extend or contract text selection horizontally or vertically.
- Added private adaptive touch calibration and expanded explicit voice-editing commands.

## 2.0.60 — version code 111

- Snippet pages now loop continuously in both directions.
- Shorter horizontal swipes change snippet pages sooner while preserving normal taps and vertical gestures.
