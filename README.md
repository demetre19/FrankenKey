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
Version: 2.0.7
Version code: 58
SHA-256: 75ee12dfb83907f1805a29cd5575b60b13dcc450d80f913dbbd0434a7219b0b1
```

This repository is now a distribution page for the finished product. Development source files, build scripts, generators, tests, and temporary project files have been removed from the current tree so the repo only presents the installable app and product information.

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
- Clean numeric and symbol pages.
- Clipboard history for up to 50 recent clips.

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
