# juloo.keyboard2 DOX

## Purpose

- Own FrankenKey app/IME Java behavior.

## Ownership

- Parent owns runtime integration; indexed children own focused domains and Reader files own their named responsibilities.

## Local Contracts

- Preserve clean/dense modes and local/private user data. Migrate callsites cleanly; no compatibility shims.
- Suggestions/autocorrect use one async session decoder with atomic request/layout/resource/privacy identity. Enable for non-secret text (including search/URI/email/web) despite no-suggestions flags; exclude passwords/numeric/phone/unknown. Structured/terminal fields remain stateless.
- Grammar and multimodal voice are explicit opt-ins with bounded exact requests, revalidation, Android-service disclosure, safe command grammar, and fail-closed destructive actions. Grammar submits only the latest completed sentence after `.`, `!`, or `?`.
- Paste/delete holds use independent intervals; repeated holds emit no release action. Backspace repeats letters, then supports reversible word/sentence selection; release deletes exactly the preview.
- Termux `TYPE_NULL` uses raw DEL/local replacement and no persistence/grammar/voice. CMUX uses only `deleteSurroundingText(1,0)`. Normal editors verify mutation before DEL fallback to prevent double deletion.
- G alone starts accelerated four-direction cursor movement and receives a faint availability border; Shift extends/reverses selection. Other letter swipes preserve global learn/forget gestures; numeric/symbol word deletion remains intact.
- Extra Keys uses one saved-order row, ≤3 expanded rows, and manager-driven visibility/order/custom modifier combinations. Return inserts newline; Shift+Tab is temporary; modifiers toggle persistently until retapped/session reset.
- Autocapitalization and standalone `i` repair only verified prose boundaries; URI/web/email/password excluded. A deliberate Shift toggle overrides automatic sentence case for the current word. UTF-16 deltas synchronize state.
- Vocabulary learning requires either explicit teaching or an editor-verified backspace/edit followed by selecting the intended suggestion. Ordinary commits, automatic autocorrection, and consecutive-word use never create unigram, bigram, or correction evidence. Taught 2–3-letter tokens retain exact casing; Taught and correction-backed Adaptive lists remain distinct. One-time policy migration preserves explicitly taught words and historical correction targets, removes passive-only words/bigrams, and preserves touch calibration. Settings keeps ≥8dp text-input start padding, alphabetical search, 1–9/10+ filters, direct teaching, confirmed forgetting, and confirmed clear-all.
- Late spellcheck retains ≤48 completed-word requests and may repair an unchanged exact range within ≤3 following sentences/768 UTF-16 units after connection, selection, cursor, source, and suffix revalidation.
- Sentence-case prose converts unspaced `word.word` periods only when the following word completes; preserve lowercase dotted text, ellipses, recognized common domains, URL/email markers, structured fields, and terminals. Backspace/cursor movement settles accepted corrections and never restores misspellings.
- Settings uses an independent dark Material theme and provides a top-level local filter across setting titles and summaries. Update checks run only from unlocked launcher/Settings, default daily, show changelog, require accept/reject, authenticate APK, and preserve data.
- Reader entry: private `ReaderActivity`; exported `ReaderShareActivity` accepts bounded `text/plain` Share/read-only Process Text via opaque one-shot token. URL-only shares use hardened extraction or fail closed.
- Reader speed/WPM, pitch, voice, network opt-in, theme, and progress persist privately. Playback state survives activity/service/process recreation.
- Library uses migration-versioned private storage, normalized-hash replacement with stable ID/progress, validated locators, owned-file deletion, local search, and safe URL Original actions.
- Documents accept granted `content://` only with bounded strict parsing. EPUB rejects traversal/expansion/encryption/XXE/spine abuse; article fetches permit public HTTP(S) 80/443 only with redirect/address/media/time/size bounds; PDF/EPUB validate content, not names.
- Saved progress uses `unit:<ordinal>:<offset>` or `article:<offset>`. Reader renders bounded units/images and maps progress without widening Quick Read.
- Keyboard Reader is default-off. Enabled empty readable editors show compact Read Clipboard/Library plus a direct Settings cog; input/candidates hide them. Explicit playback replaces candidates with title, transport, and full-width WPM slider.
- Full Reader source actions order Original/Read Clipboard/Library. Sticky transport keeps previous/play-next/stop/speed; pitch/follow/voice/network follow content. Theme/follow/highlight/scroll/jump-to-bottom/header marquee remain contrast- and overflow-aware.
- Retained images are bounded, private, ordered, aspect-fit, and open a private background-decoded zoom/pan viewer without moving playback.
- Voice rows use human names, flags, truthful offline/network status, and gender only for documented exact IDs.
- Parser dependencies stay pinned/reviewed; additions/upgrades require source/health and malformed-input verification.

## Work Guidance

- Use LSP for exported symbols; prefer feature-local changes and explicit API guards.

## Verification

- Run focused `test/juloo.keyboard2` classes; release-visible changes require signed release build and canonical APK copy. Updater changes require version-to-version device verification.

## Child DOX Index

- `ReaderLibrary.java` — private library/progress/assets.
- `ReaderLibraryActivity.java` — library UI/open/delete.
- `ReaderActivity.java` — Reader UI/playback/document mapping.
- `ReaderImageViewer.java` — private image viewer.
- `ReaderShareActivity.java` — bounded external intake.
- `ReaderArticleImporter.java` — hardened article import.
- `ReaderEpubImporter.java` — safe EPUB parsing.
- `ReaderPdfImporter.java` — bounded PDF parsing.
- `ReaderImportPipeline.java` — confirmation/persistence/handoff.
- `suggestions/AGENTS.md` — decoder/ranking/personalization.
- `autocorrect/AGENTS.md` — Hunspell JNI.
- `snippets/AGENTS.md` — snippets.
- `prefs/AGENTS.md` — settings widgets.
- `dict/AGENTS.md` — dictionaries.
- `lang/AGENTS.md` — language packs.
