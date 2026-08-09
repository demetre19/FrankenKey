<p align="center">
<img width="512" height="512" alt="5e6e1c78-a129-483f-8a2e-66a8be1e4639" src="https://github.com/user-attachments/assets/23ff41a0-2f2b-4176-a2d0-8486fc2d0f55" />
</p>

<h1 align="center">FrankenKey</h1>

<p align="center">
  Fast everyday typing and a full coding keyboard in one Android app.
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

Optional online GIF search is the one network-backed feature. If you add your own free GIPHY API key, GIF searches and the key are sent to GIPHY's API.

## Adaptive learning

FrankenKey learns how you type without uploading your typing history.

Vocabulary enters FrankenKey only when:

- You explicitly teach a word with **Teach**, the keyboard teach gesture, or the suggestion teach affordance.
- You go back into a word, edit/delete part of it, then choose the intended variation from Suggestions.

Ordinary typing, repeated commits, consecutive words, and automatic autocorrection do not teach vocabulary. On first launch after this policy update, FrankenKey removes passive-only Adaptive words and old consecutive-word memory while preserving explicitly taught words, historical correction-backed targets, and touch calibration.

You stay in control:

- Press Backspace immediately after a fresh autocorrection to restore the exact original text.
- Swipe up on the keyboard to teach the current word when Suggestions are enabled.
- Swipe down to forget the current word, then confirm the removal.
- Use **Clear adaptive learning** in Settings to remove taught and correction-backed words, typo-correction patterns, and touch calibration.

Passwords and unsupported or no-suggestions fields are excluded from adaptive learning.

## Suggestions and autocorrect

Suggestions and autocorrect are separate controls, and both are enabled by default.

- **Suggestions** help complete the word you are typing and surface likely next words.
- Swipe left across the suggestion row to bring the next three ranked words in from the right; swipe right to return to the first three.
- **Autocorrect** fixes high-confidence mistakes when you finish a word with Space, Enter, or supported punctuation.
- Explicit teaching and deliberate correction choices improve local ranking without turning ordinary typing into vocabulary.
- Immediate Backspace safely undoes a new autocorrection.
- Automatic capitalisation handles sentence starts and standalone lowercase `i` where the text field allows it.
- Short two- and three-letter corrections combine touch-aware evidence with compact conversational context.
- Explicitly taught short commands keep their casing, and manually turning Shift off preserves lowercase at sentence starts.

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

## Clipboard

FrankenKey includes a keyboard-native clipboard panel for text and images.

- Keeps up to 50 recent clipboard entries.
- Supports text clips, image clips, and recent screenshots when permission is enabled.
- Lets you pin important clips so they remain available.
- Lets you remove individual clips.
- Lets you open and edit clipboard text before pasting it.
- Uses a configurable history duration.

Clipboard data remains local to FrankenKey.

## Cursor navigation, selection and deletion

The **G** key is a convenient four-direction cursor touchpad, marked by a very faint border when navigation is available:

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

## Reader

Reader is an optional, private text-to-speech experience that is disabled by default. Enable **Reader and text-to-speech controls** in Settings when you want it.

- Read ordinary clipboard text or prose from the current field without leaving the keyboard.
- Import text, PDF, and EPUB documents, or save supported public articles to the searchable Library.
- Pause, resume, seek, follow highlighted text, adjust reading speed from the full Reader or directly beneath the keyboard and clipboard playback controls, and choose available voices.
- Keep playback progress and Reader preferences across sessions.
- View retained article images in reading order. Large images fill the reading column and open in a full-screen viewer with pinch zoom, panning, and double-tap reset.

Reader content, progress, and retained files stay in FrankenKey's private app storage. Sensitive fields and unavailable content fail closed.

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
Version: 2.0.97
Version code: 148
SHA-256: e7a67874e1f0eb2df22b31dd868861c30654f33d8521004fe7a3e0a646e2a834
Signing certificate SHA-256: 9fdb36334eb40c87d174a2dca1f5efa26e7e7cf52b0f63aac2ac1d507d4376d9
```

Android may require one uninstall before installation if an older FrankenKey APK was signed with a different key. Builds signed with the current FrankenKey release key update normally after that.

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
