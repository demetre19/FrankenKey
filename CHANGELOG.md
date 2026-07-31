# FrankenKey changelog

This file summarizes the 10 most recent major user-visible changes. For downloadable builds and complete release notes, see [GitHub Releases](https://github.com/demetre19/FrankenKey/releases).

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

## 2.0.59 — version code 110

- Expanded English autocorrect accuracy and safety across everyday writing.
- Added bounded previous-word context, broader contraction and typo handling, and stronger protection for names, slang, and technical terms.

## 2.0.58 — version code 109

- Improved responsiveness and accuracy for decisive three-letter corrections.
- Prevented short prefix completions from outranking stronger same-length corrections and bounded late replacements to unchanged recent text.

## 2.0.57 — version code 108

- Completed full-word spellchecks after continued typing.
- Corrected well-supported longer typos while avoiding guesses when multiple words were equally plausible.

## 2.0.56 — version code 107

- Improved autocorrect during continuous typing, including transpositions and missing or extra letters.
- Preserved ambiguous short words and protected technical terms during editor refreshes.

## 2.0.55 — version code 106

- Improved adjacent-key, transposition, omission, and trailing-letter correction without hardcoded word pairs.
- Prevented accepted corrections from reverting when Backspace was pressed or the cursor moved.
