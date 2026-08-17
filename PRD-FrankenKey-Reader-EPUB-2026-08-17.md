# FrankenKey Reader EPUB Library and Book AI

**Status:** Implemented, emulator-verified, and signed Samsung candidate uploaded and byte-verified on 2026-08-17  
**Build authorization:** Exact `START BUILD` and separate signed-candidate upload approval received; installation and publication remain unauthorized  
**Post-candidate correction state:** The Open With, Reader color/contrast, compact icon-control, and per-chapter AI corrections documented below are included in the current signed release-identity Samsung candidate. The canonical and uploaded APKs are 14,454,210 bytes with SHA-256 `d894b903587aef6c320c02b3f1bd8fbf0013a65a67c6610f140be4d0c00a9f7b`; the candidate is uploaded but has not been installed on the physical phone, committed, pushed, tagged, archived, or published.  
**Target:** FrankenKey Android Reader, Reader Library, Classic EPUB reader, 3D Reader, and Reader AI  
**Source repository:** `../FrankenKey-autobuild-autocorrect/`  
**Delivery repository:** `../FrankenKey/`

## Approved interface diagram

```text
READER LIBRARY
+------------------------------------------------+
| <  Reader Library                 Search  Sort |
| [ Books ] [ Articles & Text ]                  |
| [All] [Favorites] [Business] [Read Next] [...] |
+------------------------------------------------+
| [ 3:4 cover ] [ 3:4 cover ] [ 3:4 cover ]     |
| Title         Title         Title               |
| Author        Author        Author              |
| 42% --------  New --------  18% --------        |
|                                                |
| [ 3:4 cover ] [ 3:4 cover ] [ 3:4 cover ]     |
| Compact dark cards; no decorative shelves      |
+------------------------------------------------+

CLASSIC EPUB READER
+------------------------------------------------+
| <  Truncated title                       42%   |
|    Author                                      |
|================ cyan progress =================|
|                                                |
| Rich, sanitized EPUB chapter content           |
| Safe local book images                         |
| Continuous follow-along scrolling              |
|                                                |
+------------------------------------------------+
| [−]  [+]  [Font]  [Images]  [Theme]  [AI]  [3D] |
+------------------------------------------------+
             one tap, exact position
                         <->
+------------------------------------------------+
| Existing FrankenKey 3D Reader                  |
| Open-book icon returns to Classic at exact place|
+------------------------------------------------+
```

## PRD

## 1. Product overview

Add a private EPUB library to FrankenKey Reader. An Android user can share an EPUB from Files or choose FrankenKey from Android's exact-EPUB Open With flow, organize it in a compact cover-based Books tab, read it in a rich Classic EPUB view, switch to the existing 3D Reader in one tap, and resume at the same exact location in either mode.

The feature also brings the proven Private Drive Book AI workflow into FrankenKey. It reuses FrankenKey's existing encrypted OpenRouter key, native Reader AI surface, Mercury default, saved-output library, and direct OpenRouter integration. It ports Private Drive's chapter-aware chunking, summaries, quizzes, grounded book chat, evidence reuse, and cancellation/resume behavior without adding a Cloudflare dependency or a second AI interface.

The personal library is local and user-owned. It is not a bookstore, hosted service, synchronization product, or DRM bypass. EPUB files remain in a user-selected Android Books folder wherever durable folder access is available. FrankenKey stores references, metadata, a small cover cache, organization, progress, settings, and explicitly saved AI results, not a second app-private EPUB or a permanent full-text copy.

## 2. Objectives and success

### Objectives

- Make EPUB import from Android Files feel native and reliable.
- Provide a compact, dark, mobile-first book library with no shelf decoration.
- Preserve rich EPUB structure and safe packaged images in Classic mode.
- Make Classic and 3D reading two views of one book and one position.
- Resume each book exactly where the user stopped.
- Reuse the existing Reader AI system and OpenRouter key for full Book AI parity.
- Keep books, covers, progress, collections, prompts, and saved outputs private and local unless the user explicitly invokes OpenRouter or Android Share.
- Remain responsive with up to 500 books on a typical supported Android phone.

### End-to-end success

A user can:

1. Grant FrankenKey access to a Books folder once.
2. Share a valid EPUB to FrankenKey or open it with FrankenKey from Android Files.
3. Open it immediately without a repeated permission prompt.
4. See one deduplicated cover card in the Books tab.
5. Search, sort, favorite, and place the book in multiple collections.
6. Read rich sanitized chapters in Classic mode.
7. switch to 3D with one tap and continue at the same raw word.
8. Return to Classic with one tap and continue at the same raw word.
9. Close and reopen the book in its last mode at its last position.
10. Generate either configurable summary, a quiz containing the selected 6/10/12/20 questions for every readable chapter, or grounded chat using the existing OpenRouter configuration.
11. Cancel and resume supported Book AI jobs without losing completed evidence.
12. Save, copy, share, or Speed Read eligible AI outputs.
13. Find saved Book AI outputs in the existing Saved AI library even after the source book is removed.

## 3. Audience and primary job

The audience is an Android FrankenKey Reader user with a personal collection of DRM-free EPUB books. The user wants one uncluttered place to organize, read, speed-read, study, and question those books without creating an account or uploading a library.

Primary job:

> Open one of my own EPUBs, continue exactly where I stopped, switch reading modes without losing my place, and use grounded AI when I deliberately request it.

## 4. Evidence and parity baseline

### FrankenKey baseline

The current Android source already provides:

- `ReaderShareActivity.java`: controlled Reader intake, currently exposed for `text/plain` only.
- `ReaderEpubImporter.java`: defensive EPUB validation and plain chapter extraction.
- `ReaderImportPipeline.java`: background import and Reader launch.
- `ReaderLibrary.java`: private SQLite items, stable IDs, content hashing, owned preview cleanup, and progress locators.
- `ReaderLibraryActivity.java`: searchable dark Reader item list.
- `ReaderActivity.java`: native plain-text 2D Reader with 3D entry.
- `Reader3dActivity.java` and `assets/reader_3d.html`: local 3D reading, chapter mapping, progress persistence, and private settings.
- Existing Reader AI: Summary One, Summary Two, Chat, Quiz, Copy, Save, Share, Read, encrypted OpenRouter key, live model selection, Mercury default, and searchable saved results.

Current gaps:

- The Android manifest and share intake do not accept EPUB `ACTION_SEND` streams.
- EPUB import is available only through an unadvertised document-open path.
- The parser persists normalized plain text, not rich sanitized chapter HTML or cover art.
- The library is a vertical mixed-content list, not a compact book-cover grid.
- Classic reading is plain text rather than a real EPUB reading surface.
- Classic and 3D do not share a single exact raw-word coordinate in both directions.
- There is no per-book last-mode record or new-book global mode default.
- The current long-article AI path does not implement Private Drive's chapter-aware reusable evidence pipeline.

### Private Drive parity source

The canonical parity implementation is in `../GMB-Extractor/worker-setup/worker.js`, with behavior covered by `../GMB-Extractor/tests/private-drive-mobile-reader.test.js` and the desktop counterpart.

The required parity behaviors are:

- OPF metadata and spine parsing.
- EPUB2/EPUB3 cover discovery and a 3:4 thumbnail.
- Rich sanitized chapter HTML with only safe packaged images.
- Compact full-screen Classic reader with title, author, percent, cyan progress, continuous scrolling, and fixed bottom controls.
- Persisted light/sepia/dark theme, 14-32px size, serif/system font, and images on/off.
- Exact raw-word handoff between Classic and RSVP/3D.
- Summary One, Summary Two, Quiz, grounded follow-up chat, AI Bookmarks, settings, model search, and editable prompts.
- Chapter-aware deterministic evidence chunking and reuse.

## 5. Confirmed product decisions

- Scope is DRM-free EPUB only. PDF and other ebook formats are out of scope.
- Books live in a new Books tab inside the existing Reader Library.
- Existing articles and text retain their current library presentation under Articles & Text.
- The Books tab uses a compact grid with at most three covers across.
- Narrow portrait layouts may use two columns; sufficiently wide portrait or landscape layouts may use three.
- Collections appear as compact horizontal filter chips under search, with a Manage action for full editing.
- The first release includes search, sorting, favorites, and user-created collections.
- One book may belong to multiple collections without duplicating its file, metadata, progress, or cover cache.
- The feature is a private personal library only; there is no store, catalog, purchasing, login, or hosted synchronization.
- The performance target is 500 books.
- Classic EPUB structure and control placement follow the approved Private Drive layout.
- All reader controls live in the fixed bottom toolbar.
- Classic and 3D switch in one tap and resume at the same exact location.
- Full Private Drive-style Book AI is in scope for this PRD.
- Book AI reuses FrankenKey's existing OpenRouter key and current Reader AI surface.
- Saved Book AI outputs live in the existing Saved AI library.
- Android Share and Open With advertise only exact `application/epub+zip`; Open With accepts only a temporary read-granted `content://` URI and never broadens FrankenKey into a general file receiver.
- Books/filter selection uses the Reader mint accent with graphite text rather than platform blue.
- Classic exposes all seven appearance, AI, and 3D controls as icon-only actions in one compact row in portrait and landscape; 3D exposes its Classic return as an icon-only open-book action.
- Eventual signoff requires focused tests, emulator proof, and a signed Samsung test candidate only after separate artifact-transfer approval.
- Installation, commit, push, tag, archive, and public release remain separately authorized actions.

## 6. Core user flows

### 6.1 First Books use

1. User opens Reader Library and selects Books.
2. FrankenKey explains that a user-owned folder avoids private duplicate EPUB files.
3. Android's Storage Access Framework opens `ACTION_OPEN_DOCUMENT_TREE`.
4. User selects a Books folder.
5. FrankenKey retains read/write access only to the selected tree: read access keeps referenced books available, while write access allows an outside shared EPUB to become the single canonical file in that folder.
6. The Books tab appears, with an import hint when empty.

FrankenKey must not request broad all-files access.

### 6.2 Share or open an EPUB from Android Files

1. User invokes Android Share on an EPUB or chooses FrankenKey from the exact-EPUB Open With resolver.
2. FrankenKey accepts the granted `content://` stream only, then validates MIME, extension, stream magic, size, ZIP safety, EPUB container, OPF, spine, encryption state, and readable content.
3. If the EPUB is already inside the approved Books tree, FrankenKey stores a durable reference to the existing document.
4. If the EPUB is outside the tree, FrankenKey copies it once into the user-visible Books folder while the temporary Share or Open With grant is valid.
5. FrankenKey does not create another app-private EPUB copy.
6. Metadata and cover generation run off the UI thread.
7. Duplicate detection resolves the stable library item before insertion.
8. The book opens immediately using the appropriate last-mode rule.

Failure must leave no partial document, orphan cover, duplicate card, or misleading library row.

### 6.3 Open and resume

- Tapping a cover opens the book at its exact saved raw-word position.
- An existing book reopens in that book's last-used Classic or 3D mode.
- A newly imported book uses the global most-recent reader mode as its initial mode.
- If no mode history exists, Classic is the default.
- Reader styling settings persist across books; progress and last mode are per book.
- Progress writes are throttled and also flushed on pause, mode switch, chapter transition, and activity exit.

### 6.4 Missing or moved source

- A missing source does not silently delete the card, collections, progress, or saved AI results.
- The card shows Source unavailable and offers Locate Book and Remove from Library.
- Locate Book uses `ACTION_OPEN_DOCUMENT`, validates the selected EPUB, compares identity, and rebinds the existing item.
- A successful rebind preserves stable ID, collections, favorite state, AI links, and position.

### 6.5 Collections

- All and Favorites are built-in filter chips and cannot be deleted.
- User collections have a unique stable ID and editable display name.
- A book can belong to zero or many user collections.
- Adding or removing collection membership never copies or deletes the EPUB.
- Deleting a collection removes only membership records.
- Removing a book from one collection does not remove it from the library or other collections.
- Deleting a book from the library requires explicit confirmation and does not delete the user-owned source file unless a separate, clearly labeled file-deletion action is added in a future scope.

## 7. Books library experience

### 7.1 Structure

The Reader Library top level contains:

- Books.
- Articles & Text.

The Books surface contains:

- compact title bar;
- search;
- sort control;
- horizontally scrollable collection chips;
- Manage collections action;
- responsive two/three-column cover grid;
- empty, loading, unavailable-source, import-failure, and no-results states.

### 7.2 Cover card

Each card contains:

- 3:4 cover image or restrained FrankenKey fallback cover;
- title, limited to a compact readable line count;
- author when present;
- thin progress line;
- percent read or New;
- favorite state;
- accessible overflow/details action without permanent button clutter.

Cards must not use wooden shelves, shelf shadows, fake paper stacks, or decorative room imagery.

### 7.3 Search and sort

Search covers normalized title and author. It must not parse EPUB bodies during library search.

Sort options:

- Recently opened.
- Title.
- Author.
- Reading progress.

Sort, active collection, and search state may be restored while the library remains in the same task, but a stale search must not surprise the user after a later cold launch.

### 7.4 Performance

- Library queries use indexed metadata and membership tables.
- Cover thumbnails are generated once, bounded to Private Drive's approximately 280x420 JPEG target, and loaded lazily.
- Grid scrolling must not open or parse EPUB ZIPs.
- Scanning, hashing, metadata extraction, cover generation, and missing-source reconciliation run off the main thread.
- The acceptance dataset is up to 500 valid EPUB references with a realistic mix of missing covers, long titles, collections, and partial progress.

## 8. EPUB storage and identity

### 8.1 Source ownership

The user-selected Books folder is the canonical file location. FrankenKey stores a document URI and durable tree relationship, not an app-private EPUB duplicate.

Persisted FrankenKey data may include:

- stable book ID;
- canonical document URI/reference;
- source fingerprint and file metadata;
- title, author, language, publisher, and identifiers when present;
- cover thumbnail cache;
- collection memberships and favorite state;
- reading position and last mode;
- reader display settings;
- saved AI outputs and deterministic AI evidence manifests.

FrankenKey must not persist:

- a second full EPUB in app-private storage;
- a permanent full-text book extraction;
- a permanent rich-HTML duplicate of the whole book;
- raw OpenRouter credentials outside the existing encrypted key store.

A bounded transient parse/cache may exist only for the active reading or AI job and must be cleaned after use or lifecycle recovery according to explicit retention rules.

### 8.2 Deduplication

- Exact same-source references resolve to one stable book record.
- Content fingerprinting prevents a second card when the same EPUB is imported through another URI or filename.
- Reimporting an exact duplicate focuses/opens the existing record and preserves progress, favorite state, collections, and saved AI links.
- A changed file at the same URI triggers metadata/content refresh and a safe progress remap based on text anchors and prior percent, with a clear fallback when exact remap is impossible.
- No deduplication decision may merge different books solely because title and author match.

## 9. Safe rich EPUB parsing

Retain and extend the existing defensive importer:

- Validate ZIP magic and EPUB `mimetype`.
- Reject encrypted/DRM-protected reading content with a clear unsupported message.
- Reject path traversal, absolute paths, unsafe encodings, excessive expansion ratio, oversized entries, excessive total expansion, malformed XML, missing container/OPF/spine, and unreadable content.
- Resolve OPF manifest and spine order.
- Extract metadata and cover candidates from EPUB2 meta cover, EPUB3 `cover-image`, and safe cover-like manifest fallbacks.
- Remove scripts, forms, iframes, objects, embeds, remote links/resources, event handlers, unsafe SVG, active styles, and navigation capable of leaving the book.
- Permit only a narrow sanitized chapter markup allowlist.
- Permit packaged images only after validating media type, entry bounds, and decoded dimensions.
- Render safe packaged images through app-controlled data or resource paths; never grant the WebView arbitrary filesystem or `content://` access.
- Produce both transient rich chapters and deterministic normalized plain text/raw-word mappings from the same parse.

## 10. Classic EPUB reader

### 10.1 Layout

Classic is a full-screen local reading surface with:

- compact Back action;
- truncated title;
- author when available;
- percent read;
- thin cyan progress line;
- rich continuously scrollable chapter content;
- one fixed compact bottom row containing icon-only A−, A+, font family, images, theme, AI, and 3D controls.

Content, not chrome, remains visually primary.

### 10.2 Display controls

Persist the proven Private Drive ranges and states:

- text size from 14px to 32px;
- serif/system font toggle;
- packaged images on/off;
- dark, sepia, and light theme cycle.

Controls require descriptive accessibility labels, at least 48dp targets, borderless visible focus/pressed state, and immediate content feedback without losing the reading anchor. All seven actions remain on one compact line in portrait and landscape. Dark, sepia, and light apply coherent contrast to the content, native header, icons, and Android system bars.

### 10.3 WebView boundary

The Classic WebView is a local renderer, not a browser:

- network access disabled for book content;
- file/content access disabled unless an app-controlled safe loader requires a narrower audited path;
- no arbitrary navigation;
- no untrusted script execution;
- only the smallest native bridge needed for progress, mode switch, and reader controls;
- bridge input validated and incapable of exposing the OpenRouter key, saved AI data, unrestricted files, or native methods.

## 11. Shared Classic/3D progress

### 11.1 Canonical coordinate

Use one canonical raw-word coordinate derived from normalized spine-order text. Maintain chapter index, character offset, percent, and a short text anchor as secondary recovery fields.

Required mappings:

- rich DOM position to raw-word coordinate;
- raw-word coordinate to rich DOM position;
- existing 3D chunk `rawStart` to canonical coordinate;
- canonical coordinate to the paused 3D chunk;
- canonical coordinate to Reader Library percent.

### 11.2 One-tap mode switch

- Classic bottom toolbar contains an icon-only accessible 3D action.
- 3D contains an icon-only open-book action labeled for returning to the Classic reader.
- Switching first captures the current canonical coordinate, then opens the other mode paused at that coordinate.
- The destination must not auto-advance until its position is applied.
- Mode switching updates the per-book last mode.
- Repeated switching must not accumulate measurable drift.

### 11.3 Progress compatibility

Existing chapter/character progress records require a clean migration to the canonical coordinate. Migration must preserve readable progress for existing EPUB library items and must not alter non-EPUB Reader items.

## 12. Book AI experience

### 12.1 Surface reuse

Extend FrankenKey's existing native Reader AI workspace. Do not create a second Book AI dialog, a second encrypted key store, a second model picker, or a Cloudflare-backed path.

Book context labels must identify title, author, chapter coverage, selected model, and multi-call/cost implications without crowding the compact surface.

### 12.2 Generate modes

Provide exactly:

- Summary One.
- Summary Two.
- Quiz with 6, 10, 12, or 20 questions for every readable spine chapter; non-book articles retain the selected whole-article count.

Provide Generate and Cancel while work is active. During book work, the output area reports granular chapter, evidence, and question progress rather than only an indeterminate busy state.

Results provide:

- Copy.
- Save.
- Share.
- Speed Read for summaries and chat answers.
- No Speed Read action for Quiz.

### 12.3 Grounded chat

After a summary or quiz result, and from direct Chat entry:

- Ask a question.
- Copy answer.
- Share answer.
- Save through the existing output workflow where supported.
- Retrieve up to 12 highest lexical-overlap book passages.
- Fall back to the first three chapters when lexical retrieval yields no useful match.
- Treat every source passage as untrusted data, not instructions.
- Refuse unsupported claims and clearly state when the book does not contain the answer.

### 12.4 Settings

Reuse the existing Reader AI settings:

- encrypted OpenRouter key;
- live searchable model list;
- Free filter;
- 100k+ context filter;
- pricing/context display;
- Mercury as the default when available;
- editable Summary One, Summary Two, and Quiz prompts.

Book-specific prompt defaults adapt the current Reader prompts to books and chapters without creating duplicate setting identities unless separate defaults are technically required and approved during implementation planning.

## 13. Chapter-aware Book AI pipeline

Port the proven Private Drive algorithm into the local FrankenKey AI path.

### 13.1 Input limits

- Up to 6 MiB normalized book text.
- Up to 200 chapters.
- Up to 240 safe chunks.
- Limits must fail clearly before paid dispatch.

### 13.2 Chunk planning

- Split on chapter-aware paragraph boundaries.
- Derive chunk budget from the selected model's live context.
- Reserve 8k tokens for instructions, evidence framing, synthesis, and output.
- Use no more than half of remaining model context per source chunk.
- Bound individual chunks between approximately 1k and 24k tokens.
- Preserve chapter identity, order, paragraph range, and raw-word range.
- Dispatch at most four OpenRouter requests concurrently.

### 13.3 Neutral evidence and synthesis

- Build deterministic neutral evidence for each source chunk.
- Cache completed evidence by book fingerprint, parser/pipeline version, model identity, and chunk plan.
- Reuse valid evidence for Summary One, Summary Two, Quiz, and resumed jobs without rechunking or retransmitting unchanged source unnecessarily.
- Each summary independently summarizes every readable spine chapter from grounded evidence, then deterministically assembles one labeled chapter section per source chapter in source order.
- No readable chapter may be omitted or merged into an unconstrained final synthesis.
- Detailed and concise summaries retain independent prompts and target allocations.
- Quiz generates the selected 6/10/12/20 questions for every readable chapter, dispatching batches of at most three questions and assembling chapters in source order.

### 13.4 Reliability

- Jobs use deterministic IDs and persisted resumable state.
- Completed evidence survives activity recreation and app restart according to bounded retention rules.
- Cancellation stops undispatched work, ignores late lifecycle results safely, and retains reusable completed evidence.
- Permit one retry for an empty model response.
- Permit one bounded quiz-format repair.
- Authentication, rate limit, timeout, offline, cancellation, provider, malformed response, parser, storage, and source-moved failures are explicit and non-destructive.
- Failed follow-up chat leaves the prior conversation visible.

## 14. Saved AI library integration

The existing Saved AI library gains:

- source type filter: All, Articles, Books;
- output type filter: Summary, Quiz, Chat where supported;
- title/author/source metadata for book outputs;
- search over saved output and source metadata;
- Open Book when the source remains available;
- Copy, Share, Speed Read where eligible, Favorite, and Delete;
- source-unavailable state without deleting saved content.

Saved Book AI results survive removal or loss of the source EPUB. They retain readable output and a bounded evidence/provenance manifest but not a permanent full-book extraction.

## 15. Conceptual data model

### Book

- `id`: stable UUID/string.
- `documentUri`: canonical Android document reference.
- `treeUri`: owning persisted Books tree reference.
- `contentSha256`: source fingerprint.
- `byteSize`: long.
- `lastModified`: nullable long.
- `title`: string.
- `author`: nullable string.
- `language`: nullable string.
- `publisher`: nullable string.
- `identifier`: nullable string.
- `coverCachePath`: nullable private thumbnail path.
- `sourceState`: available, missing, invalid, changed.
- `favorite`: boolean.
- `createdAt`, `updatedAt`, `lastOpenedAt`: timestamps.

### Book progress

- `bookId`: foreign key.
- `rawWordIndex`: long canonical position.
- `chapterIndex`: int secondary position.
- `charOffset`: int secondary position.
- `textAnchor`: bounded normalized recovery text.
- `percent`: bounded numeric display value.
- `lastReaderMode`: Classic or 3D.
- `updatedAt`: timestamp.

### Collection

- `id`: stable UUID/string.
- `name`: unique normalized display name.
- `sortOrder`: int.
- `createdAt`, `updatedAt`: timestamps.

### Book collection membership

- `bookId`: foreign key.
- `collectionId`: foreign key.
- Unique composite key prevents duplicate membership.

### Reader EPUB settings

- `booksTreeUri`: nullable persisted tree reference.
- `globalLastReaderMode`: Classic or 3D.
- `classicTheme`: dark, sepia, light.
- `classicTextSize`: bounded numeric value.
- `classicFontFamily`: serif or system.
- `classicImagesEnabled`: boolean.

### Book AI job

- `jobId`: deterministic stable ID.
- `bookId`: foreign key while available.
- `bookFingerprint`: source SHA-256.
- `featureType`: Summary One, Summary Two, Quiz, Chat.
- `promptHash`, `modelId`, `pipelineVersion`, `chunkPlanHash`: identity fields.
- `status`: pending, running, cancelled, failed, complete.
- `completedChunkIds`: bounded set.
- `createdAt`, `updatedAt`: timestamps.

### Book AI evidence

- `evidenceId`: deterministic ID.
- `job/cache identity` fields.
- `chapterIndex`, `paragraphRange`, `rawWordRange`.
- `neutralEvidence`: bounded model output.
- `model/provenance metadata`.
- No raw OpenRouter key and no permanent full-source text.

Existing Saved AI items are extended with nullable `sourceType`, `bookId`, `bookFingerprint`, `bookTitle`, `bookAuthor`, and bounded provenance fields.

## 16. Security and privacy

- Update the existing first-use Reader AI disclosure to state that explicitly requested book excerpts may be sent to the selected OpenRouter model.
- Require this updated disclosure once before first Book AI use.
- After acceptance, only a deliberate Generate, Quiz, or Chat action sends content.
- Never upload during import, scan, cover generation, library browsing, reading, progress save, or mode switching.
- Do not upload an entire library or another book.
- Show a multi-call notice before a paid or multi-request Book AI job begins.
- Use the existing encrypted, backup-excluded OpenRouter key.
- Keep the key and networking in native Android code; no WebView receives credentials or raw AI request/response plumbing.
- Keep saved outputs, evidence, jobs, metadata, covers, collections, and progress private/local until explicit Android Share.
- Treat EPUB markup and text as untrusted input.
- Frame book content as source data with prompt-injection-resistant delimiters.
- Enable no tools, URL fetching, filesystem access, or external actions for model responses.
- Reject active EPUB content and remote resources.
- Do not bypass or attempt to decrypt DRM.

## 17. Backup and restore

Backup may include:

- book metadata records;
- collection definitions and memberships;
- favorites;
- reading progress and mode;
- eligible Classic settings;
- editable prompts and non-secret AI settings;
- saved AI outputs and bounded provenance.

Backup excludes:

- OpenRouter key;
- persisted URI permission grants;
- source EPUB files;
- regenerable covers;
- transient parsed chapters/images;
- raw full-book text;
- incomplete temporary files;
- disposable evidence cache unless explicitly proven safe and useful.

After restore, book records without access show Locate Book. Rebinding the same fingerprint restores progress, collections, favorites, and saved-output links.

## 18. Accessibility and responsive behavior

- Minimum 48dp touch targets for all icon actions.
- Descriptive content labels for covers, favorite, overflow, collection controls, reading controls, AI, Classic, 3D, Copy, Save, Share, Speed Read, and Close.
- Scalable text and high contrast in dark, sepia, and light themes.
- Standard Android Back behavior from AI to reader, reader to library, and collection management to Books.
- Grid adapts between two and three columns without cropped covers or unreadable metadata.
- Long titles/authors truncate predictably and remain available to accessibility services and details.
- Cover fallbacks do not depend on color alone.
- Progress is exposed semantically, not only as a line.
- Classic reader retains the current reading anchor after font, size, image, orientation, or theme changes.
- Reader and AI controls remain above navigation and display cutout insets in portrait and landscape.
- No decorative motion is required.

## 19. Functional requirements and acceptance criteria

### F001 - One-time Books folder access

- User grants a Books tree through the system picker once.
- Permission persists across restart when the provider supports it.
- The persisted grant is scoped read/write access to the selected tree, never broad storage access.
- FrankenKey requests no broad all-files permission.
- Revoked access produces a recoverable state.

### F002 - Exact EPUB Share and Open With target

- FrankenKey appears for exact EPUB Share streams and exact EPUB Open With intents without becoming a general unsafe file receiver.
- Open With requires `ACTION_VIEW`, `application/epub+zip`, a `content://` URI, and the temporary read grant; PDF and unrelated MIME types do not resolve to FrankenKey.
- A valid Share or Open With action imports or references the book and opens it.
- Invalid MIME/stream/grant combinations fail safely.

### F003 - No private EPUB duplication

- Books inside the approved tree are referenced directly.
- External shared or opened books are copied once into the user-visible tree.
- No second app-private EPUB remains after success or failure.

### F004 - Safe EPUB parser

- Valid DRM-free EPUB2/EPUB3 fixtures import metadata, spine, rich chapters, plain mapping, safe images, and cover.
- DRM, traversal, ZIP bomb, oversized, malformed, missing-spine, active-content, unsafe SVG, and remote-resource fixtures are rejected or sanitized as specified.

### F005 - Deduplication and rebind

- Reimporting identical content never creates a second card.
- Existing progress, favorite, collections, and saved AI links remain.
- Locate Book rebinds a matching source without resetting the item.

### F006 - Books grid

- Books and Articles & Text are distinct library tabs.
- Books displays two or three columns, never more than three.
- Covers use 3:4 framing with compact title, author, and progress.
- No shelf decoration appears.
- Selected Books and filter controls use the Reader mint/graphite state and do not fall back to platform blue.

### F007 - Search, sort, favorites, and collections

- Title/author search and all four sorts work with 500 books.
- All, Favorites, and user collections work as filter chips.
- A book can belong to multiple collections without duplication.
- Collection deletion never deletes a book.

### F008 - Classic EPUB reader

- Rich sanitized spine-order chapters render with safe packaged images.
- Seven icon-only A−, A+, font, images, theme, AI, and 3D controls work from one compact 52dp bottom row in portrait and landscape, with ≥48dp targets and accessible labels.
- Display changes preserve reading position.

### F009 - Exact Classic/3D handoff

- Classic-to-3D and 3D-to-Classic are one-tap icon-only actions; the 3D return uses an accessible open-book icon.
- Destination opens paused at the same canonical raw word.
- Repeated round trips do not introduce cumulative drift beyond the current visible word/chunk boundary.

### F010 - Resume and last mode

- Reopening a book restores its exact position and per-book last mode.
- New books use the global last-used mode, with Classic as first-run default.
- Progress persists through pause, process recreation, orientation change, and normal app exit.

### F011 - Book AI disclosure and settings

- Existing encrypted OpenRouter key and model settings are reused.
- Updated first-use disclosure explicitly covers book excerpts.
- Mercury remains preferred when available.
- No Book AI request occurs on open/import/read.

### F012 - Chapter-aware evidence pipeline

- Limits, chunk sizing, order, provenance, maximum concurrency, deterministic identity, and cache reuse follow Section 13.
- Every readable spine chapter is represented by one labeled full-book summary section in deterministic source order.
- Resuming a job reuses completed valid evidence rather than retransmitting it.

### F013 - Two summaries

- Summary One and Summary Two use their exact editable prompts and independent identities.
- Each summary is grounded chapter-by-chapter in complete reusable evidence, with no unconstrained final synthesis and no omitted readable chapter.
- Copy, Save, Share, Speed Read, Cancel, and supported resume behavior work.

### F014 - Quiz

- For books, Quiz returns exactly the selected 6, 10, 12, or 20 grounded pre-reading questions for every readable chapter; article Quiz retains the selected whole-article total.
- Book questions are generated per chapter in batches of at most three and assembled in chapter order.
- One bounded repair handles format/count failure.
- The visible output area reports granular chapter/question progress while the quiz is running.
- Quiz has no Speed Read action.

### F015 - Grounded book chat

- Direct and follow-up chat retrieve bounded relevant passages.
- Answers cite or identify supporting book context when useful.
- Unsupported questions receive a clear source-not-found answer.
- Copy and Share answer work; failure preserves the prior conversation.

### F016 - Saved AI integration

- Existing Saved AI library filters All/Articles/Books and Summary/Quiz/Chat where supported.
- Saved Book AI outputs remain readable after source removal.
- Open Book appears only while the source is available.

### F017 - Failure handling

- Offline, timeout, cancellation, rate limit, authentication, provider, malformed output, storage, parser, permission, moved source, over-limit source, and lifecycle completion failures are clear and non-destructive.
- Partial imports are removed.
- Late AI completions cannot update a closed or replaced surface incorrectly.

### F018 - Backup and recovery

- Eligible metadata, organization, progress, settings, and saved outputs round-trip.
- Key, EPUB files, URI grants, covers, full extracted text, and transient work do not.
- Restored books can be rebound without losing user state.

### F019 - 500-book performance

- Search, sort, collection filtering, and grid scroll remain responsive at 500 books.
- No EPUB body parsing occurs during grid binding or search.
- Background tasks do not block the main thread.

### F020 - Existing Reader regression safety

- Non-EPUB text, clipboard, and URL Reader items continue to work.
- Existing URL/clipboard Reader AI privacy and eligibility remain correct.
- Existing 3D settings, progress, follow-along behavior, light/dark themes, and navigation-inset protections remain intact.
- Package remains `dev.frankenkey.keyboard` with label FrankenKey and the established release signer.

## 20. Non-functional requirements

- Match existing Java/XML and local WebView asset patterns.
- Reuse current Reader library, 3D, AI, encrypted key, saved results, and OpenRouter components.
- Do not introduce a second architecture beside an existing one.
- Add no cloud backend and no account system.
- Add no dependency unless a proof shows it is necessary, locally bundleable, license-compatible, and safer/smaller than extending current code.
- No network or ZIP parsing on the IME main thread or Android UI thread.
- Database migrations are transactional and backward compatible with current Reader data.
- Temporary files are bounded, private, and cleaned after failure/cancellation.
- Logging excludes book text, EPUB paths beyond safe diagnostics, OpenRouter key, model payloads, saved output bodies, and private collection names where unnecessary.

## 21. Costs and scalability

- FrankenKey has no hosted inference or recurring backend cost.
- The user pays OpenRouter directly through their own key.
- Live model pricing/context is shown when OpenRouter supplies it.
- Multi-call Book AI work is disclosed before dispatch.
- Evidence reuse should reduce repeated cost without weakening cache identity.
- Local storage is primarily the user's EPUB folder. FrankenKey storage grows with metadata, covers, collections, saved AI outputs, and bounded evidence, not duplicate EPUB files.
- The required library performance envelope is 500 books; larger libraries are best-effort until separately specified.

## 22. Risks and mitigations

- **Temporary Share URI expires:** copy external shares once into the approved Books tree while the grant is valid.
- **Provider cannot retain tree access:** surface a clear reconnect flow; never claim the file is stored when it is not.
- **Book moved or deleted:** preserve metadata/state and provide Locate Book.
- **Duplicate imports:** fingerprint before insert and reuse the stable record.
- **Malicious EPUB:** retain strict ZIP/XML/markup/image bounds and disable active WebView capabilities.
- **DRM:** reject clearly; do not bypass.
- **Position drift:** use one raw-word coordinate and automated round-trip fixtures across rich DOM and 3D chunks.
- **Large-book memory pressure:** parse chapters incrementally, bound transient caches, and avoid permanent full-text duplication.
- **AI prompt injection:** source-delimit content, keep feature prompt as system authority, enable no tools, and require grounded refusal.
- **Unexpected OpenRouter cost:** show pricing/context and multi-call notice, cap chunks/concurrency, support cancellation/resume, and reuse deterministic evidence.
- **AI result survives missing book:** preserve readable saved output/provenance but disable Open Book until rebind.
- **500-cover jank:** lazy-load bounded thumbnails and keep ZIP parsing away from grid binding.
- **Regression in existing Reader:** isolate EPUB presentation while reusing one library/progress/AI contract and run focused existing Reader tests.

## 23. Development phases

### Phase A - Grounding and executable proofs

- Freeze fixtures and parity contracts.
- Prove persisted tree access plus shared external copy behavior on supported Android providers.
- Prove rich sanitized chapter rendering with safe packaged images under the intended WebView boundary.
- Prove raw-word round-trip mapping between Classic DOM, persisted progress, and 3D chunks.
- Prove Private Drive chapter-aware chunk planning and evidence identity locally against deterministic fixtures.

### Phase B - Storage and library foundation

- Add schema/migrations for books, progress, collections, memberships, EPUB settings, and Book AI extensions.
- Add one-time Books tree setup, source reconciliation, deduplication, and Locate Book.
- Extend EPUB metadata/cover parsing without permanent full-book extraction.
- Build Books tab, grid, search, sorts, favorites, chips, and collection management.

### Phase C - Classic reader and shared progress

- Build secure local Classic EPUB surface.
- Implement rich chapter loading, safe images, themes, typography, and continuous follow-along scrolling.
- Establish canonical raw-word progress and migrate current EPUB locators.
- Add exact one-tap Classic/3D handoff and per-book last mode.

### Phase D - Book AI parity

- Reuse existing settings/key/model surface and update disclosure.
- Port chapter-aware evidence planning, deterministic cache, cancellation, retry, repair, and resume.
- Add Summary One, Summary Two, Quiz, and grounded book chat.
- Integrate Copy, Save, Share, Speed Read, and existing Saved AI filters.

### Phase E - Verification and candidate preparation

- Run focused parser, storage, library, progress, WebView, AI, backup, and regression tests.
- Exercise real emulator flows and visually verify compact portrait/landscape surfaces.
- Verify the 500-book dataset path.
- Only after separate approval, build the signed release-identity Samsung test candidate and transfer exact verified bytes.
- Do not install, commit, push, tag, archive, or publish without the applicable separate approval.

## 24. Lightweight implementation planning package

This project uses standard Codex todo mode, one active item at a time. It will not launch AutoBuild, subagents, schedulers, or parallel worktrees. Exact `APPROVE PRD` authorizes this plan only. Exact `START BUILD` is required before Item 1 begins.

| ID | Work item | Depends on | Completion evidence |
|---|---|---|---|
| T01 | Freeze EPUB, Private Drive, and current Reader fixtures/contracts | - | Fixture inventory and focused baseline results |
| T02 | Prove durable Books tree and external-share copy path | T01 | Executable Android proof covering persisted/revoked access |
| T03 | Prove safe rich EPUB chapter/image rendering | T01 | Malicious/valid fixture proof under locked WebView settings |
| T04 | Prove canonical Classic/3D raw-word mapping | T01 | Deterministic round-trip mapping proof with no cumulative drift |
| T05 | Prove local chapter-aware AI evidence pipeline | T01 | Fixture-driven chunk, identity, reuse, resume, and limit proof |
| T06 | Add book/library schema and migrations | T02, T04 | Focused migration and rollback tests |
| T07 | Implement Books folder setup, share intake, dedupe, and rebind | T02, T06 | Focused intake tests plus emulator share/rebind proof |
| T08 | Extend EPUB metadata, cover, rich parse, and transient cache | T03, T06 | EPUB2/3, cover, image, DRM, bomb, traversal, and cleanup tests |
| T09 | Build Books tab, grid, search, sort, favorites, and collections | T06, T08 | UI behavior proof with 500-book fixture dataset |
| T10 | Build secure Classic EPUB reader and display controls | T03, T08 | Real emulator rich-reading and control proof |
| T11 | Implement shared progress migration and Classic/3D handoff | T04, T06, T10 | Reopen and repeated bidirectional handoff proof |
| T12 | Integrate Book AI disclosure, settings, and chapter evidence | T05, T08 | Focused privacy, identity, cancellation, and resume tests |
| T13 | Add summaries, quiz, grounded chat, and result controls | T12 | Mode-specific output, grounding, retry/repair, and action tests |
| T14 | Extend Saved AI, backup, restore, and missing-source recovery | T06, T13 | Persistence, filters, rebind, and backup round-trip tests |
| T15 | Run focused regression and full emulator acceptance flow | T07-T14 | Recorded commands/results and portrait/landscape interaction evidence |
| T16 | Prepare signed Samsung candidate only after separate approval | T15 | Release identity, signatures, package/label/version, size, and SHA-256 evidence |

Planning review rules:

- Load only the active item's source, fixtures, nearest DOX chain, and directly relevant evidence.
- Mark an item complete only with the evidence listed above.
- If a proof fails, revise the active design/PRD assumption before production implementation.
- Do not begin a dependent item until every dependency is complete.
- Do not silently narrow acceptance criteria.
- If the scope later requires unattended, parallel, resumable, or independent signoff, stop and recommend `/autobuild`; never switch automatically.

## 25. Verification and signoff

Feature signoff requires:

- Valid and adversarial EPUB fixtures.
- Android Files share and one-time Books tree flows.
- No private duplicate EPUB after success or failure.
- Two/three-column compact library proof at representative portrait and landscape sizes.
- Search, all sorts, favorites, multiple collections, missing source, and Locate Book.
- Rich Classic rendering, safe images, all bottom controls, and reading-anchor preservation.
- Exact Classic/3D switch and cold-resume position.
- Full Book AI modes, deterministic evidence reuse, grounded refusal, cancellation, resume, and failure handling.
- Saved AI source/type filtering and missing-source behavior.
- Backup/restore and key exclusion.
- 500-book responsiveness.
- Existing Reader/3D/URL/clipboard AI regression coverage.
- Production package identity remains `dev.frankenkey.keyboard`, label FrankenKey, and established signer.
- Signed Samsung candidate only after explicit transfer approval.

No public release is implied by feature signoff.

## 26. Explicitly out of scope

- PDF, MOBI, AZW, CBZ, CBR, audiobook, or other format support.
- DRM removal or decryption.
- Commercial bookstore, purchases, subscriptions, or publisher accounts.
- Public-domain catalog or automatic internet book discovery.
- FrankenKey-hosted cloud library, cross-device sync, or account authentication.
- Broad all-files Android permission.
- Permanent app-private EPUB duplicate.
- Permanent full-text/rich-HTML duplicate of every book.
- Background AI generation or library upload.
- AI tools that browse the web or act outside the selected book.
- Decorative shelves or skeuomorphic bookcase imagery.
- More than three cover columns.
- Physical-phone installation, commit, push, tag, archive, or publication without separate authorization.

## 27. References

Repository evidence:

- `../FrankenKey-autobuild-autocorrect/srcs/juloo.keyboard2/ReaderShareActivity.java`
- `../FrankenKey-autobuild-autocorrect/srcs/juloo.keyboard2/ReaderEpubImporter.java`
- `../FrankenKey-autobuild-autocorrect/srcs/juloo.keyboard2/ReaderImportPipeline.java`
- `../FrankenKey-autobuild-autocorrect/srcs/juloo.keyboard2/ReaderLibrary.java`
- `../FrankenKey-autobuild-autocorrect/srcs/juloo.keyboard2/ReaderLibraryActivity.java`
- `../FrankenKey-autobuild-autocorrect/srcs/juloo.keyboard2/ReaderActivity.java`
- `../FrankenKey-autobuild-autocorrect/srcs/juloo.keyboard2/Reader3dActivity.java`
- `../FrankenKey-autobuild-autocorrect/assets/reader_3d.html`
- `../GMB-Extractor/worker-setup/worker.js`
- `../GMB-Extractor/tests/private-drive-mobile-reader.test.js`

Platform references:

- Android Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files
- Android secure file sharing: https://developer.android.com/training/secure-file-sharing
- Android document provider guide: https://developer.android.com/guide/topics/providers/document-provider
- OpenRouter API: https://openrouter.ai/docs/api/reference/overview

## 28. Approval record

- Product discovery and approved interface diagram confirmed on 2026-08-17.
- Storage model confirmed: one-time Books tree; reference inside-tree books; copy outside-tree shares once into that user-visible folder; no app-private EPUB duplicate.
- Library scope confirmed: Books tab, compact filter chips, search, sort, favorites, multi-collection membership, max three covers, 500-book target.
- Reader scope confirmed: rich Classic EPUB, one-tap exact Classic/3D handoff, shared resume.
- Book AI scope confirmed: Private Drive chapter-aware chunking and controls, existing encrypted OpenRouter key, updated one-time disclosure, existing Saved AI library.
- Exact `APPROVE PRD` received on 2026-08-17. Lightweight planning is authorized.
- Exact `START BUILD` received on 2026-08-17. Implementation and emulator acceptance are complete. Separate candidate approval was then received; the original signed production-identity APK was copied to the canonical delivery path and uploaded, but not installed. The later correction candidate replaced it at `/storage/emulated/0/Download/FrankenKey-installable-release.apk`; current local and remote files are 14,454,210 bytes with SHA-256 `d894b903587aef6c320c02b3f1bd8fbf0013a65a67c6610f140be4d0c00a9f7b`.
