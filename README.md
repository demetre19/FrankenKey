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
</p>

---

## Private by design

FrankenKey keeps its adaptive typing data on your device. Remembered words, word pairs, typo corrections, snippets, settings, and clipboard history are stored locally by FrankenKey. There are no ads and FrankenKey does not include tracking or analytics.

Optional online GIF search is the one network-backed feature. If you add your own free GIPHY API key, GIF searches and the key are sent to GIPHY's API.

## Adaptive learning

FrankenKey learns how you type without uploading your typing history.

It can remember:

- Words you accept and use.
- Word pairs that help predict what comes next.
- Your repeated typo corrections.
- Corrections you explicitly teach.

Learning starts helping from the first accepted evidence. Four repeated corrections of the same typo can make FrankenKey strongly prefer your correction, even when the original text looks like a valid word.

You stay in control:

- Press Backspace immediately after a fresh autocorrection to restore the exact original text.
- Swipe up on the keyboard to teach the current word when Suggestions are enabled.
- Swipe down to forget the current word, then confirm the removal.
- Use **Clear adaptive learning** in Settings to remove remembered words, word-pair memory, and typo-correction weights.

Passwords and unsupported or no-suggestions fields are excluded from adaptive learning.

## Suggestions and autocorrect

Suggestions and autocorrect are separate controls, and both are enabled by default.

- **Suggestions** help complete the word you are typing and surface likely next words.
- **Autocorrect** fixes high-confidence mistakes when you finish a word with Space, Enter, or supported punctuation.
- Local word, word-pair, and correction memory improves ranking around your own typing habits.
- Immediate Backspace safely undoes a new autocorrection.
- Automatic capitalisation handles sentence starts and standalone lowercase `i` where the text field allows it.

You can turn Suggestions or Autocorrect off independently in Settings.

## Two keyboard modes

FrankenKey combines two keyboard ideas:

- **Everyday mode** uses a clean Fleksy-style layout for messages, notes, search, and normal phone typing.
- **Coding mode** uses a dense Unexpected Keyboard-style layout for code, Termux, SSH, remote computers, and configuration files.

Swipe northeast from the Spacebar to switch modes at any time.

### Everyday mode

Everyday mode is the default. It includes:

- Clean QWERTY rows with minimal visual noise.
- A simple bottom row with `123`, `Fn`, Space, punctuation, and Enter.
- Fast edit gestures on `z`, `x`, `c`, and `v` for Select all, Cut, Copy, and Paste.
- Clean numeric and symbol pages.
- Voice typing on the bottom-right action key without crowding the Spacebar.
- Hidden power gestures that remain available without filling every key with labels.

### Coding mode

Coding mode adds compact access to:

- `Ctrl`, `Fn`, `Alt`, and `Meta`.
- Arrow and navigation keys.
- Tab and Esc.
- Brackets, braces, pipes, slashes, quotes, operators, and shell punctuation.
- Extra keys that can be enabled individually in Settings.

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

## Swipe to delete

Everyday mode supports reversible word deletion from the middle letter row:

1. Start on any middle-row letter and swipe left.
2. Continue left to select more words or punctuation chunks.
3. Slide back to the right before release if you selected too much.
4. Lift your finger to delete the highlighted text.

Backspace also supports normal character deletion, accelerated deletion while held, and a separate word-delete corner gesture.

## Snippets

Save phrases as snippet buttons above the keyboard, then tap to insert them at the cursor.

- Seven snippet slots per page.
- Add more pages when needed.
- Swipe the snippet row horizontally to change pages.
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

Current release:

```text
Package: dev.frankenkey.keyboard
Version: 2.0.49
Version code: 100
SHA-256: a9ed938b39d4498f4492fbee2820d591decbe70c5ff80a605814f740a1f3f5b4
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
