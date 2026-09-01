<p align="center">
<img width="512" height="512" alt="5e6e1c78-a129-483f-8a2e-66a8be1e4639" src="https://github.com/user-attachments/assets/23ff41a0-2f2b-4176-a2d0-8486fc2d0f55" />
</p>

<h1 align="center">FrankenKey</h1>

<p align="center">
  Fast everyday typing, customizable coding keys, 2D/3D RSVP speed reading, and private TTS tools in one Android app.
</p>

<p align="center">
  <strong>Private by design. Local learning. No ads. No tracking.</strong>
</p>

<p align="center">
  <a href="FrankenKey-installable-release.apk"><strong>Download the latest APK</strong></a>
  ·
  <a href="CHANGELOG.md"><strong>Recent changes</strong></a>
</p>

---

## Private by design

FrankenKey keeps its adaptive typing data on your device. Explicitly taught words, deliberate correction choices, typo-correction patterns, touch calibration, snippets, settings, and clipboard history are stored locally by FrankenKey. There are no ads and FrankenKey does not include tracking or analytics.

Optional online GIF search and opt-in Reader AI are the only network-backed features. If you add your own GIPHY API key, GIF searches and the key are sent to GIPHY's API. Reader AI sends only a safe public article, text you explicitly loaded with **Read Clipboard**, or selected excerpts from a safely parsed EPUB, and only after you request an AI action, to the OpenRouter model you choose. PDF content and passively observed clipboard or editor text are never eligible.

## Adaptive learning

FrankenKey learns how you type without uploading your typing history or silently turning repeated mistakes into vocabulary.

Vocabulary enters FrankenKey only when:

- You explicitly enter a word with **Teach** or choose **Keep word** from a keyboard review.
- You go back into a word, edit/delete part of it, then choose the intended variation from Suggestions.

Ordinary typing, automatic autocorrection, and consecutive words never teach vocabulary. Repeating the same exact unknown literal three times in one safe editor session opens a review, but that bounded counter stays in memory and disappears when the editor session ends.

You stay in control:

- Press Backspace immediately after a fresh autocorrection to restore the exact original text.
- Swipe up on the keyboard to review the current word instead of saving it immediately; choose **Keep word**, **Use best**, **Replace…**, or dismiss without changing anything.
- Use **Learned words > Corrections** to see, search, edit, or delete exact `source → replacement` rules and `source → Best suggestion` decisions.
- Convert an accidentally taught word into a correction directly from its Taught words row.
- Exact user-authored replacements take precedence even when the source and intended word are unrelated. Adaptive correction evidence still helps immediately; correcting the same typo to the same intended suggestion four times makes that learned pair the strongest recall, including supported corrections up to two textual edits.
- Use **Clear adaptive accuracy** in Settings to remove taught words, correction patterns and rules, and touch calibration.

Passwords, structured fields, terminals, and other unsafe editors are excluded from adaptive learning and replacement review.

## Suggestions and autocorrect

Suggestions and autocorrect are separate controls, and both are enabled by default.

- **Suggestions** help complete the word you are typing and surface likely next words.
- Swipe left across the suggestion row to bring the next three ranked words in from the right; swipe right to return to the first three.
- **Autocorrect** fixes high-confidence mistakes when you finish a word with Space, Shift+Enter, or supported punctuation.
- Explicit teaching and deliberate correction choices improve local ranking without turning ordinary typing into vocabulary.
- Immediate Backspace safely undoes a new autocorrection.
- Automatic capitalisation handles sentence starts and standalone lowercase `i` where the text field allows it.
- Short two- and three-letter corrections combine touch-aware evidence with compact conversational context.
- Explicitly taught short commands keep their casing, and manually turning Shift off preserves lowercase at sentence starts.
- Default-on Typing assistance turns two consecutive spaces in prose into a full stop and one space; structured fields and terminals keep literal spaces.

You can turn Suggestions or Autocorrect off independently in Settings.

## Two keyboard modes

FrankenKey combines two layouts:

- **Everyday mode** uses a clean layout for messages, notes, search, and normal phone typing.
- **Coding mode** uses a dense layout for code, terminals, SSH, remote computers, and configuration files.

Swipe northeast from the Spacebar to switch modes at any time.

### Everyday mode

Everyday mode is the default. It includes:

- Clean QWERTY rows with minimal visual noise.
- A simple bottom row with `123`, `Fn`, Space, punctuation, and Enter.
- Fast edit gestures on `z`, `x`, `c`, and `v` for Select all, Cut, Copy, and Paste.
- Clean numeric and symbol pages.
- Voice typing on the bottom-right action key without crowding the Spacebar.
- A Keyboard setting can hide the dedicated full-stop key beside the Spacebar and expand the Spacebar into the freed width.
- Hidden power gestures that remain available without filling every key with labels.

### Coding mode

Coding mode adds compact access to:

- `Ctrl`, `Fn`, `Alt`, and `Meta`.
- Arrow and navigation keys.
- Tab and Esc.
- Brackets, braces, pipes, slashes, quotes, operators, and shell punctuation.
- A configurable Extra Keys strip with navigation, editing, modifier, shortcut, and function keys.

### Extra Keys

Tap `…` on the keyboard to open the Extra Keys strip. Tap it again to expand up to three horizontally scrollable rows.
Build the strip like a compact desktop terminal command bar rather than accepting a fixed layout:

- Use `+` to open **Extra Keys Bar** settings.
- Show or hide every shortcut.
- Drag shortcuts into your preferred order.
- Add custom combinations using `Ctrl`, `Alt`, `Shift`, or `Cmd` with a supported key or keyboard command.

## Spacebar shortcuts

The four corners of the Spacebar open FrankenKey's most-used tools:

- Swipe northwest for **Clipboard**.
- Swipe northeast to switch **Everyday and Coding modes**.
- Swipe southwest for **Emoji**.
- Swipe southeast for **GIFs**.

The welcome screen animates these exact gestures with the same icons shown on the live keyboard.

## Keyboard shortcut map

The same compact map is available from **Keyboard shortcut map** below the Spacebar guide on FrankenKey's first-launch screen.

| Action | Shortcut or gesture |
|---|---|
| Send, search, go, or run the field action | **Enter** |
| Insert a line break | **Shift + Enter** |
| Voice typing | **Enter ↖** |
| Move the cursor in any direction | **G + swipe** |
| Select text; reverse the swipe to shrink | **Shift + G + swipe** |
| Select all | **Z ↖** |
| Cut / Copy / Paste | **X ↖ / C ↖ / V ↖** |
| Delete the previous word | **Backspace ←** |
| Preview and delete more words or sentences | Hold **Backspace**, drag left, reverse to shrink, then release |
| Review / forget the current word | With Suggestions on, swipe **↑ / ↓** from an ordinary letter |
| Review one recurring unknown pattern | Type the same exact unknown literal three times in one editor session |
| Make one exact adaptive typo correction the strongest recall | Correct the same typo to the same intended word and choose it **four times** |

In terminal-style fields that do not advertise an editor action, bare Enter falls back to a raw Enter key event. `Ctrl`, `Alt`, or `Meta` combinations with Enter also remain raw key combinations.

## Clipboard

FrankenKey includes a keyboard-native clipboard panel for text and images.

- Keeps up to 50 recent clipboard entries.
- Supports text clips, image clips, and recent screenshots when permission is enabled.
- Lets you pin important clips so they remain available.
- Lets you remove individual clips.
- Lets you open and edit clipboard text before pasting it.
- Uses a configurable history duration.

Clipboard data remains local to FrankenKey.

## Images and voice

When the current field is empty, the compact keyboard Reader controls keep Settings, Read Clipboard, Library, Attach Image, and Voice reachable even on narrow screens or with larger text.

- **Attach Image** opens Android's image picker and inserts the selected image into compatible rich-content editors.
- Unsupported editors get an honest handoff to the app's own attachment control instead of a false success.
- **Voice** starts Android voice input, while Reader provides separate spoken playback with selectable installed voices.

## G-key mouse-like cursor control, selection and deletion

The **G** key works like a miniature mouse/trackpad for the text cursor, marked by a very faint border when navigation is available:

- Swipe left or right from G to move through text.
- Swipe up or down from G to move between lines.
- Continue farther from the starting key to move faster.
- Reverse direction to move back precisely.
- Tap without swiping to type the letter normally.

To delete a selection:

1. Tap **Shift**.
2. Swipe from **G** to select text in any direction.
3. Reverse the swipe to shrink the selection when needed.
4. Tap **Delete**.

For progressive deletion, you can still hold Delete to repeat letters, continue left to highlight words and sentences, reverse to shrink the highlight, and lift to delete it.

## Reader and text-to-speech

Reader is an optional, private reading and text-to-speech workspace that is disabled by default. Enable **Reader and text-to-speech controls** in Settings when you want it.

### Text-to-speech Reader

- Read ordinary clipboard text or prose from the current field without leaving the keyboard.
- Import text, PDF, and EPUB documents, or save supported public articles to the searchable Library.
- Pause, resume, seek, follow highlighted text, adjust reading speed from the full Reader or directly beneath the keyboard and clipboard playback controls, and choose available voices.
- Keep playback progress and Reader preferences across sessions.
- View retained article images in reading order. Large images fill the reading column and open in a full-screen viewer with pinch zoom, panning, and double-tap reset.

### 2D and 3D RSVP speed reader

Open the **RSVP speed reader** for rapid serial visual presentation, which displays one word or word group at a time at your chosen pace:

- **2D mode** is a focused RSVP speed reader that shows one word or word group at a time.
- **3D mode** pairs the same RSVP text in adjustable stereo positions while preserving the same document position. The paired view is inspired by the [SEO Time Machines 3D Speed Reader](https://seotimemachines.com/productivity-tools/speed-reader/).

To find a comfortable 3D view:

1. Pause playback, start at a low WPM, and choose a comfortable font size.
2. Enable **3D** so the same word appears on the left and right.
3. Relax your gaze and look gently through the screen until the two copies merge into one apparent word in the centre. Do not force your eyes.
4. Adjust **stereo spacing** until the centre image is easy to hold, then start playback.
5. Increase WPM gradually. Use fewer words per group or return to 2D whenever the image stops merging comfortably.

If the effect causes eye strain, headache, or persistent double vision, stop, take a break, and use 2D mode. The stereo view is an optional reading technique, not medical treatment.

[![Watch the 3D Speed Reader demonstration](https://img.youtube.com/vi/X-8emb9-vOw/hqdefault.jpg)](https://www.youtube.com/watch?v=X-8emb9-vOw)

Reader controls also let you:

- Preserve the same document position when switching between 2D and 3D.
- Control WPM, words per group, adaptive short-word grouping, font, spacing, word and focus colours, focus position, pinned focus letters, guide lines, and crosshairs.
- Tune punctuation, sentence-end, and long-word pauses.
- Show customizable chapter cues and progress, save Reader bookmarks, and search saved places.
- Choose dark or light themes, decorative backgrounds, background depth, a text vignette, and stereo spacing.
- Use touch gestures for play/pause, font size, speed, word distance, sentence navigation, start/end jumps, and plain-text view.
- Keep grouped, responsive settings usable on narrow phones.

### Reader AI

Reader AI is optional and available for:

- Safe public articles saved or opened in Reader.
- Non-empty text you explicitly load with **Read Clipboard**.
- Safely parsed EPUB books opened from the Books Library in Classic or 3D Reader.

Nothing is sent when you open Reader or a book. Reader AI runs only after you deliberately request a Summary, Quiz, or Chat response.

#### Set up Reader AI

1. Open an eligible article, clipboard text, or EPUB book.
2. Tap **AI** in Classic Reader or **Book AI** in 3D Reader.
3. Open AI Settings and enter your own OpenRouter API key.
4. Keep the default Mercury model or choose another available model. Model search includes Free and 100k+ context filters.
5. Review the first-use disclosure, then confirm before sending any source text.

The OpenRouter key is encrypted in Android's secure local storage and excluded from backups.

#### Create summaries

Reader AI provides exactly two summary choices. Each has its own editable prompt, so you can keep different formats—for example, a short practical brief and a detailed chapter-by-chapter explanation.

1. Open the AI workspace and select **Summary 1** or **Summary 2**.
2. Tap the selected summary action.
3. Leave the workspace open to watch chapter and evidence progress.
4. Copy, Save, Share, or **Read** the finished result. **Read** strips safe Markdown and opens the generated summary in the speed reader.

For EPUB books, summaries cover every readable spine chapter in source order. FrankenKey reuses safe cached evidence but rejects obsolete partial whole-book outputs, so an older result that stopped early does not hide later chapters.

#### Create a quiz

1. Open the AI workspace and choose **Quiz**.
2. Select 6, 10, 12, or 20 questions per readable book chapter. Articles use the selected whole-article question count.
3. Run the quiz and follow chapter/question progress in the output area.
4. Save, copy, or share the result when it is ready.

Book quizzes continue to later chapters if one chapter returns an incomplete response. The partial chapter is shown and cached instead of discarding the whole run. Run **Quiz** again to reuse completed chapters and resume only the missing questions.

#### Ask grounded questions

Choose **Chat**, enter a question, and send it. Book answers are grounded in selected source passages plus reusable book evidence. If the source does not contain the answer, Reader AI is required to say so rather than inventing one.

#### Find saved results

Open AI Settings and choose the saved-results library. You can search, sort newest or oldest, group by date, mark favourites, and filter by Articles or Books and Summary, Quiz, or Chat. Saved entries retain source provenance and can reopen an available original book. Copy, Share, Favorite, and Delete remain local actions until you explicitly share something; eligible non-Quiz outputs can also open in Speed Read.

Reader content, progress, bookmarks, retained files, and saved AI outputs stay private to FrankenKey. Selected eligible text is sent only for the explicit OpenRouter request you approve. Sensitive fields, unavailable sources, PDF content, and passive clipboard/editor text fail closed.

## Snippets

Save phrases as snippet buttons above the keyboard, then tap to insert them at the cursor.

- Seven snippet slots per page.
- Add more pages when needed.
- Use short horizontal swipes to change pages; the page list loops continuously in both directions.
- Available in both Everyday and Coding modes.
- Stored locally and available on lock screens when configured.

Snippets work well for addresses, replies, commands, code fragments, and text you type repeatedly.

## Emoji and GIF search

Emoji search is built in and local.

GIF search includes built-in results and optional online GIPHY results:

1. Open **Settings > GIF search**.
2. Choose **Create a GIPHY API key**.
3. In GIPHY, choose **API**, not SDK, and create a free beta API key.
4. Paste the key into FrankenKey.
5. Swipe southeast from Space to search GIFs.

Online searches use `https://api.giphy.com/`. Search terms and your API key are therefore handled by GIPHY when this optional feature is enabled.

## Download

Install the signed APK from this repository:

```text
FrankenKey-installable-release.apk
```

Current signed repository APK:

```text
Package: dev.frankenkey.keyboard
Version: 2.0.106
Version code: 157
SHA-256: 55df96d91095f56156cbb9396d1004c36de612f1e4fa6e75b533c58f7ab36f48
Signing certificate SHA-256: 9fdb36334eb40c87d174a2dca1f5efa26e7e7cf52b0f63aac2ac1d507d4376d9
```

Android may require one uninstall before installation if an older FrankenKey APK was signed with a different key. Builds signed with the current FrankenKey release key update normally after that.

## Installing a test APK safely

Android may block an APK that was downloaded outside Google Play. Do **not** permanently disable Android security or global app updates. Use only the official FrankenKey repository or release page, confirm the package and SHA-256 above, grant the smallest temporary permission needed, and restore it after installation.

### Standard Android

1. Download `FrankenKey-installable-release.apk` from this repository or the official GitHub Release.
2. Open **Settings > Apps > Special app access > Install unknown apps**.
3. Select the app that will open the APK, such as **Chrome**, **Files**, or **My Files**.
4. Turn on **Allow from this source**.
5. Open the APK and choose **Install** or **Update**.
6. Return to **Install unknown apps** and turn **Allow from this source** off again.

Android menu names vary slightly by manufacturer and version. See [Google's official unknown-app installation guidance](https://support.google.com/android/answer/9457058).

### Samsung Galaxy and Auto Blocker

On Galaxy phones running One UI 6 or newer, **Auto Blocker** can disable APK installation:

1. Open **Settings > Security and privacy > Auto Blocker**.
2. Turn **Auto Blocker** off temporarily.
3. Grant **Allow from this source** only to the browser or file manager opening the APK.
4. Install or update FrankenKey.
5. Immediately turn **Auto Blocker** back on and remove the source permission.

See [Samsung's official Auto Blocker guide](https://www.samsung.com/us/support/answer/ANS10003636/) and [Samsung's unknown-source troubleshooting](https://www.samsung.com/us/support/troubleshoot/TSG10001913/).

### Google Play Protect

Keep Play Protect enabled whenever possible. If it warns about a test APK, stop and confirm that the APK came from `demetre19/FrankenKey`, its package is `dev.frankenkey.keyboard`, and its SHA-256 matches the value published here. Never bypass a harmful-app warning for different or unverified bytes.

If Play Protect still blocks the exact verified APK and you deliberately choose to proceed:

1. Open **Google Play Store > profile picture > Play Protect > Settings**.
2. Temporarily turn off **Scan apps with Play Protect**.
3. Install the verified APK.
4. Immediately return to the same screen and turn scanning back on.

Google documents Play Protect and its controls in [Android Help](https://support.google.com/android/answer/2812853).

### Keep a test build from being replaced

FrankenKey never silently downloads or installs an update. It checks GitHub at most once daily and requires confirmation. To stop even those prompts while testing, open **FrankenKey Settings > Updates** and turn off **Automatically check for updates**. Do not disable Play Store auto-updates for every app; FrankenKey is not Play Store-managed, and unrelated security updates should continue normally. Google's [per-app auto-update instructions](https://support.google.com/googleplay/answer/113412/update-your-android-apps) are available if another test app specifically requires them.

Updating a FrankenKey APK with the same package and release signer preserves app data. Uninstalling because of a signature mismatch removes Android app data, so back up anything important before uninstalling an older, differently signed build.

## Updates

Automatic update checks are enabled by default and run at most once every 24 hours when FrankenKey's app or Settings screen is opened. When a newer signed release is available, FrankenKey shows its changelog. Tap **Download and install** to continue, or **Not now** to suppress that release during automatic checks; a manual check can show it again.

Settings offers verified in-app APK delivery or manual download from the official GitHub Release page. Android always asks for confirmation before installation. Because updates keep the same package and signing certificate, keyboard settings, snippets, learned words, clipboard preferences, and other app data remain in place.

## Credits

### Unexpected Keyboard

FrankenKey is based on [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard), created by [Julow](https://github.com/Julow) with contributions from the Unexpected Keyboard community.

Unexpected Keyboard provides FrankenKey's open source Android keyboard foundation, compact corner-key model, and power-user layout architecture.

### Fleksy

Fleksy inspired FrankenKey's clean everyday typing mode, including its simple rows, fast symbol access, and gesture-first approach.

FrankenKey is not affiliated with, endorsed by, or sponsored by Fleksy. Fleksy is credited for design inspiration only.

## License

FrankenKey follows the license terms inherited from Unexpected Keyboard. See [LICENSE](LICENSE).

Keep upstream credit intact when sharing modified builds.
