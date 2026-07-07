<p align="center">
  <img src="logo.png" alt="FrankenKey logo" width="170" />
</p>

<h1 align="center">FrankenKey</h1>

<p align="center">
  The finished Android APK for a keyboard that blends Fleksy-style everyday typing with Unexpected Keyboard-style power controls.
</p>

<p align="center">
  <a href="FrankenKey-installable-release.apk"><strong>Download the APK</strong></a>
</p>

---

## Download

Install the final APK from this repository:

```text
FrankenKey-installable-release.apk
```

Current release:

```text
Package: dev.frankenkey.keyboard
Version: 2.0.8
Version code: 59
SHA-256: 0343891efaec8d6a830a5b8792fda44d16b86491c0f1745b58e8fdb7c8497322
```


---

## What is FrankenKey?

FrankenKey combines two keyboard ideas:

- **Fleksy-style everyday use** for fast, clean typing.
- **Unexpected Keyboard-style power use** for coding, SSH, terminal work, and compact computer controls.

The default experience is the clean Fleksy-style layout. FrankenKey's denser computer layout is optional for when you need extra keys.

---

## Everyday mode

Everyday mode is for normal typing: messages, search, notes, browser input, and daily phone use.

It includes:

- Clean QWERTY rows.
- A simple Fleksy-style bottom row:
  - `123`
  - `Fn`
  - space
  - punctuation
  - enter
- No default `Ctrl` key.
- No default arrow-key cluster.
- Hidden left-swipe word deletion on the middle row.
- Fast edit shortcuts on the bottom letter row:
  - `z` for select all
  - `x` for cut
  - `c` for copy
  - `v` for paste
- Clean numeric and symbol pages with a large voice dictation key beside `ABC`.
- Voice dictation in the top-left corner of the bottom-right action key, so Enter/Search/Done/Go layouts keep dictation available without adding clutter.
- Clipboard and emoji/GIF controls on the bottom rows for fast access to paste history, emoji search, and GIF search.
- Clipboard history for up to 50 recent clips; tap a saved clip to paste it, or pin important clips for reuse.
- Snippet expansion for saved phrases, shortcuts, and reusable text blocks; the same huge snippet feature is available in both Everyday mode and FrankenKey mode.

This is the mode to use most of the time.

---

## FrankenKey mode

FrankenKey mode is the optional compact computer layout.

Use it for:

- Coding on a phone.
- Termux.
- SSH sessions.
- Remote computer work.
- Editing config files.
- Any task that needs more than letters and basic punctuation.

It adds compact access to:

- `Ctrl`, `Fn`, `Alt`, and `Meta`.
- Arrow and navigation keys.
- Tab and Esc.
- Brackets, braces, pipes, slashes, quotes, operators, and shell punctuation.
- A `123` button for fast numeric access.

This mode is dense on purpose. It is for power work, not default typing.

### Shared headline feature: snippets

Both keyboard modes share FrankenKey's snippet feature, so saved phrases, shortcuts, and reusable text blocks are available whether you are using the clean Everyday layout or the dense FrankenKey power layout. That makes snippets a major feature instead of a mode-specific extra.

---

## Credits

FrankenKey gives full credit to the projects and creators it builds on.

### Unexpected Keyboard

FrankenKey is based on [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard), created by [Julow](https://github.com/Julow) with work from the Unexpected Keyboard contributor community.

Unexpected Keyboard provided the open source Android keyboard foundation, including the compact swipe and corner-key approach that makes FrankenKey mode possible.

### Fleksy

Fleksy inspired the clean everyday typing experience: clear rows, simple controls, fast symbol access, and gesture-first keyboard behavior.

FrankenKey is not affiliated with, endorsed by, or sponsored by Fleksy. Fleksy is credited here for design inspiration only.

---

## Privacy

FrankenKey is designed as a local keyboard. It does not need ads or tracking to work.

Clipboard history is capped at 50 recent items.

---

## License

FrankenKey follows the license terms inherited from Unexpected Keyboard. See [LICENSE](LICENSE).

Keep upstream credit intact when sharing modified builds.
