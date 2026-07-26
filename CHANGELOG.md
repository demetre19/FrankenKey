# FrankenKey changelog

This file summarizes the 10 most recent major user-visible changes. For downloadable builds and complete release notes, see [GitHub Releases](https://github.com/demetre19/FrankenKey/releases).

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

## 2.0.54 — version code 105

- Let repeated explicit corrections override stale learned history and persist across restarts.
- Added guarded correction for clear short-word mistakes and missing negative contractions while protecting ambiguous valid words.

## 2.0.53 — version code 104

- Fixed Backspace reintroducing an extra letter after an autocorrection shortened the original typo.
- Accepted corrections now delete normally instead of making visible text longer.

## 2.0.52 — version code 103

- Added correction learning tied to the exact preceding-word context.
- Fixed partial-word replacements, duplicate spaces, spaces after undo, and spaces before punctuation.

## 2.0.51 — version code 102

- Fixed capitalized words sometimes duplicating their first letter during autocorrection.
- Autocorrect now verifies the complete visible word before replacing or learning it.
