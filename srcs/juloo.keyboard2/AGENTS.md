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
- Extra Keys uses one saved-order row, ≤3 expanded rows, and manager-driven visibility/order/custom modifier combinations. Bare Return performs the editor's advertised action and falls back to raw Return when unavailable or rejected; Shift+Return inserts a literal newline; Ctrl/Alt/Meta combinations remain raw. Shift+Tab is temporary; modifiers toggle persistently until retapped/session reset.
- Autocapitalization and standalone `i` repair only verified prose boundaries; URI/web/email/password excluded. A deliberate Shift toggle overrides automatic sentence case for the current word. UTF-16 deltas synchronize state.
- Vocabulary learning requires either explicit teaching or an editor-verified backspace/edit followed by selecting the intended suggestion. Deliberate Teach persists in safe prose editors even when the host disables passive personalization; structured fields, terminals, passwords, and other unsafe editors remain excluded. Ordinary commits, automatic autocorrection, and consecutive-word use never create unigram, bigram, or correction evidence. Taught 2–3-letter tokens retain exact casing; Taught and correction-backed Adaptive lists remain distinct. One-time policy migration preserves explicitly taught words and historical correction targets, removes passive-only words/bigrams, and preserves touch calibration. Settings keeps ≥8dp text-input start padding, alphabetical search, 1–9/10+ filters, direct teaching, confirmed forgetting, and confirmed clear-all.
- Late spellcheck retains ≤48 completed-word requests and may repair an unchanged exact range within ≤3 following sentences/768 UTF-16 units after connection, selection, cursor, source, and suffix revalidation.
- Sentence-case prose converts unspaced `word.word` periods only when the following word completes; preserve lowercase dotted text, ellipses, recognized common domains, URL/email markers, structured fields, and terminals. Backspace/cursor movement settles accepted corrections and never restores misspellings.
- The default-on Double-space for full stop setting changes a second consecutive prose space after a non-punctuation character to `. `. When disabled, or in structured/terminal editors, spaces remain literal.
- Settings uses an independent dark Material theme and provides a top-level local filter across setting titles and summaries. Update checks run only from unlocked launcher/Settings, default daily, show changelog, require accept/reject, authenticate APK, and preserve data.
- Reader entry: private `ReaderActivity` and `Reader3dActivity`; exported `ReaderShareActivity` accepts bounded `text/plain` Share/read-only Process Text via opaque one-shot token. URL-only shares use hardened extraction or fail closed.
- Native Reader speed/WPM, pitch, voice, network opt-in, theme, and progress persist privately. Playback state survives activity/service/process recreation.
- Library uses migration-versioned private storage, normalized-hash replacement with stable ID/progress, validated locators, owned-file deletion, local search, and safe URL Original actions.
- Documents accept granted `content://` only with bounded strict parsing. EPUB rejects traversal/expansion/encryption/XXE/spine abuse; article fetches permit public HTTP(S) 80/443 only with redirect/address/media/time/size bounds; PDF/EPUB validate content, not names.
- Saved progress uses `unit:<ordinal>:<offset>` or `article:<offset>`. Reader renders bounded units/images and maps progress without widening Quick Read.
- The private 3D Reader hosts the exact local Private Drive mobile stereo RSVP controls/settings in a hardware-accelerated WebView. Its single inset-safe in-WebView toolbar owns Back, progress, bookmark actions, 2D/3D mode, and settings; no Brainwave control or duplicate native action bar is shown. It keeps at least 60px top clearance in portrait and 50px right clearance in landscape for edge-to-edge Samsung system bars, keeps the top inset theme-solid with decoration beginning below it, and offsets the right gesture rail by the same landscape clearance with a 120px inward thumb target. Top chapter progress stacks above all Reader chrome; settings stack above gesture rails and generic modals stack above settings. Only background slider adjustment fades the settings sheet to 1% visibility. Playback preserves the selected decorative background and vignette through the reading area, shades only the upper and lower 24% stage bands, and hides toolbar controls and WPM while preserving word count and progress indicators. The asset has no network/file/content access; text crosses an app-private handoff plus bounded 32 KiB bridge chunks, and raw-word coordinates map back to existing Reader locators.
- Safe URL-imported articles and text explicitly loaded with Read Clipboard expose the same native Reader AI workspace from 2D and 3D. OpenRouter is the only network boundary and uses a user-owned encrypted API key, explicit first-use disclosure, Mercury default with searchable model selection, two editable summary prompts, direct/follow-up chat, Article Quiz, and bounded chunk-and-combine summaries. The compact navigation-inset-safe workspace offers raw-Markdown Copy/Save/Share, brief save feedback, plain-text speed-reading for generated summaries, and an accessible bookmark icon opening its searchable/sortable/favoritable date-grouped saved-output library. Never send passively observed clipboard, EPUB, or PDF content, persist chat automatically, or conflate AI favorites with 3D position bookmarks.
- Keyboard Reader is default-off. Enabled empty readable editors show a centered, horizontally scrollable strip ordered Settings/Read Clipboard/Library/Insert Image/Voice; Insert Image is direct rich-content insertion, never a Sharesheet substitute. Compatible editors receive bounded, unique FrankenKey-owned cache URIs so delayed reads remain stable; unsupported editors get an honest app-attachment fallback message. Input/candidates hide the strip. Done/Enter retains top-left voice typing. Explicit playback replaces candidates with title, transport, and full-width WPM slider.
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
- `Reader3dActivity.java` — locked local 3D Reader host/bridge/progress mapping.
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
