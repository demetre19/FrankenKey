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
- Vocabulary learning requires either confirmed explicit teaching or an editor-verified backspace/edit followed by selecting the intended suggestion. Teach gestures never write immediately. The third exact unknown literal commit in one safe editor session opens a Keep/Best suggestion/Exact replacement review; its bounded counters never cross editor sessions or persist, while explicit replacement rules remain credential-protected and editable. Structured fields, terminals, passwords, and other unsafe editors remain excluded. Ordinary commits, automatic autocorrection, and consecutive-word use never create unigram, bigram, or correction evidence. Taught 2–3-letter tokens retain exact casing; Learned Words separates Taught literals from source-to-target Corrections. One-time policy migration preserves explicitly taught words and historical correction targets, removes passive-only words/bigrams, and preserves touch calibration. Settings keeps ≥8dp text-input start padding, alphabetical search, 1–9/10+ source-length filters, direct teaching, editable corrections, confirmed forgetting/deletion, and confirmed clear-all.
- Late spellcheck retains ≤48 completed-word requests and may repair an unchanged exact range within ≤3 following sentences/768 UTF-16 units after connection, selection, cursor, source, and suffix revalidation.
- Sentence-case prose converts unspaced `word.word` periods only when the following word completes; preserve lowercase dotted text, ellipses, recognized common domains, URL/email markers, structured fields, and terminals. Backspace/cursor movement settles accepted corrections and never restores misspellings.
- The default-on Double-space for full stop setting changes a second consecutive prose space after a non-punctuation character to `. `. When disabled, or in structured/terminal editors, spaces remain literal.
- Settings uses an independent dark Material theme and provides a top-level local filter across setting titles and summaries. Update checks run only from unlocked launcher/Settings, default daily, show changelog, require accept/reject, authenticate APK, and preserve data.
- Reader entry: private `ReaderActivity`, `ReaderEpubActivity`, and `Reader3dActivity`; exported `ReaderShareActivity` accepts bounded `text/plain` Share/read-only Process Text and exact `application/epub+zip` Share or Open With from a granted `content://` URI. Text imports retain confirmation; EPUB imports validate first, then directly reference a file already inside the persisted scoped Books tree or create one bounded canonical copy before opening.
- Native Reader speed/WPM, pitch, voice, network opt-in, theme, and progress persist privately. Playback state survives activity/service/process recreation.
- Reader Library separates EPUB Books from Articles & Text. Books use metadata-only indexed queries, a responsive compact grid capped at three covers, title/author search, Recent/Title/Author/Progress sorting, Favorites, many-to-many collections, restrained generated fallback covers, and matching-content Locate Book recovery. Selected Books and filter states use the Reader mint accent with graphite text rather than platform blue. The personal-library target is 500 books; grid queries must not hydrate retained chapter text.
- Documents accept granted `content://` only with bounded strict parsing. The Books folder uses persisted Storage Access Framework read/write access rather than broad storage permission. EPUB rejects traversal/expansion/encryption/XXE/spine abuse; article fetches permit public HTTP(S) 80/443 only with redirect/address/media/time/size bounds; PDF/EPUB validate content, not names.
- Classic EPUB reading parses rich spine content transiently from the user-owned source into a private hardware-accelerated WebView with file/content/network/storage access disabled. It renders one continuous document, preserves the visible anchor while changing 14–32px text size, serif/system font, images, or dark/sepia/light theme, and restores scroll state across orientation without persisting EPUB content. Its fixed 52dp Private Drive-style row keeps seven evenly weighted actions in this order: `A-`, `A+`, `Aa`, outline image, outline sun, `AI`, and `3D`. Compact 17sp text marks and 18dp artwork retain accessible labels, borderless pressed feedback, and at least 48dp targets in portrait and landscape; every theme applies matching native header, control, system-bar, and content contrast with Reader mint rather than platform blue.
- Saved progress uses `unit:<ordinal>:<offset>` or `article:<offset>`. Reader renders bounded units/images and maps progress without widening Quick Read.
- The private 3D Reader hosts the exact local Private Drive mobile stereo RSVP controls/settings in a hardware-accelerated WebView. Its single inset-safe in-WebView toolbar owns Back, progress, bookmark actions, an icon-only accessible Classic-book mode action, and settings; no Brainwave control or duplicate native action bar is shown. It keeps at least 60px top clearance in portrait and 50px right clearance in landscape for edge-to-edge Samsung system bars, keeps the top inset theme-solid with decoration beginning below it, and offsets the right gesture rail by the same landscape clearance with a 120px inward thumb target. Top chapter progress stacks above all Reader chrome; settings stack above gesture rails and generic modals stack above settings. Only background slider adjustment fades the settings sheet to 1% visibility. Playback preserves the selected decorative background and vignette through the reading area, shades only the upper and lower 24% stage bands, and hides toolbar controls and WPM while preserving word count and progress indicators. The asset has no network/file/content access; text crosses an app-private handoff plus bounded 32 KiB bridge chunks, and raw-word coordinates map back to existing Reader locators.
- Safe URL articles, text explicitly loaded with Read Clipboard, and safely parsed user-opened EPUB books expose the same native Reader AI workspace from Classic/2D and 3D. OpenRouter is the only network boundary and uses a user-owned encrypted API key, updated explicit first-use disclosure, Mercury default with searchable model selection and Free/100k+ filters, and exactly two editable summary prompts. Book AI reparses chapter text transiently in native code, plans deterministic chapter-aware evidence, dispatches no more than four requests at a time, and stores bounded neutral evidence/jobs only in a disposable backup-excluded work database. Each summary rejects stale whole-output cache formats and deterministically assembles one grounded section for every readable spine chapter in source order. Quiz generates the selected 6/10/12/20 questions for every readable chapter in bounded batches; completed chapters are cached and reused, partial chapters are displayed and cached without blocking later chapters, and rerunning Quiz resumes only missing questions. Articles retain whole-article quiz counts. Book work reports granular chapter/evidence/question progress in the output area. Chat is grounded in selected passages plus reusable evidence and must admit absent answers. Saved AI outputs remain durable and backup eligible without their source, retain book title/author/fingerprint/provenance, support All/Articles/Books and Summary/Quiz/Chat filters, and reopen an available book by stable id or matching content hash. Copy, Save, Share, and plain-text Speed Read apply to summaries/chat; Quiz never exposes Speed Read.
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
- `ReaderLibraryActivity.java` — Books grid/search/sort/favorites/collections/rebind plus existing article/text UI.
- `ReaderActivity.java` — Reader UI/playback/document mapping.
- `Reader3dActivity.java` — locked local 3D Reader host/bridge/progress mapping.
- `ReaderImageViewer.java` — private image viewer.
- `ReaderShareActivity.java` — bounded external intake.
- `ReaderBooksFolder.java` — persisted scoped Books tree, reference/copy source policy, and source availability.
- `ReaderArticleImporter.java` — hardened article import.
- `ReaderEpubImporter.java` — safe EPUB parsing.
- `ReaderEpubActivity.java` — private locked Classic EPUB host, continuous rich rendering, and persisted appearance controls.
- `ReaderBookAiPlanner.java` / `ReaderBookAiEvidence.java` / `ReaderBookAiWorkStore.java` — bounded chapter planning plus deterministic, cancellable, resumable, backup-excluded neutral-evidence work state.
- `ReaderAiStore.java` / `ReaderAiLibraryActivity.java` — durable backup-eligible saved AI outputs, source/output filtering, source-loss survival, provenance, and content-hash book recovery.
- `ReaderAiService.java` / `ReaderAiDialog.java` / `ReaderAiOpenRouter.java` — native Book AI generation, grounded result modes/actions, bounded concurrency, retry/repair, and cancellation transport.
- `ReaderPdfImporter.java` — bounded PDF parsing.
- `ReaderImportPipeline.java` — confirmation/persistence/handoff.
- `suggestions/AGENTS.md` — decoder/ranking/personalization.
- `autocorrect/AGENTS.md` — Hunspell JNI.
- `snippets/AGENTS.md` — snippets.
- `prefs/AGENTS.md` — settings widgets.
- `dict/AGENTS.md` — dictionaries.
- `lang/AGENTS.md` — language packs.
