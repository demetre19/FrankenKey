# Debug Deep Grounding

Run: `20260705T161907Z-5c0f9642`  
Updated: `2026-07-05T16:55:05Z`

## Level 0: known-issues and source checks

- Project memory: unavailable for this project (`memory://root` reported no artifacts).
- Current repo state: only planning artifacts exist; no application source has been imported yet.
- Upstream source: `Julow/Unexpected-Keyboard` is a Java Android IME under GPLv3. README describes it as lightweight, privacy-conscious, and based on swiping keys toward corners for extra characters.
- Android IME docs: an IME extends `InputMethodService`; Android calls `onCreateInputView()`/input view lifecycle methods, and text is sent to the focused app through `InputConnection.commitText(CharSequence, int)`.
- Fleksy APK evidence from earlier intake: hotkeys are local indexed snippets with `[isFont, icon, phrase]`, rendered in groups of seven, with editor label auto-generation.

No prior local implementation exists in this repo. Planning must therefore ground implementation in the imported Unexpected Keyboard source before editing.

## Level 1: planned flow trace from upstream source

```text
FLOW: Baseline Unexpected Keyboard input view

Step 1: IME service starts
  File: AndroidManifest.xml
  Class: juloo.keyboard2.Keyboard2
  Does: Declares InputMethodService with BIND_INPUT_METHOD and method metadata.
         |
         v
Step 2: Service initialization
  File: srcs/juloo.keyboard2/Keyboard2.java
  Fn:   onCreate()
  Does: Initializes config, suggestions, key handler, then calls create_keyboard_view().
         |
         v
Step 3: Inflate baseline keyboard container
  File: srcs/juloo.keyboard2/Keyboard2.java
  Fn:   create_keyboard_view()
  Does: Inflates R.layout.keyboard, finds R.id.keyboard_view and R.id.candidates_view.
         |
         v
Step 4: Baseline layout shape
  File: res/layout/keyboard.xml
  Does: Vertical LinearLayout containing CandidatesView then Keyboard2View.
         |
         v
Step 5: Input view shown
  File: srcs/juloo.keyboard2/Keyboard2.java
  Fn:   onStartInputView(...)
  Does: Refreshes config/layout, sets Keyboard2View keyboard, starts KeyEventHandler, calls setInputView(_keyboard_container_view).
         |
         v
Step 6: Key touch handling
  File: srcs/juloo.keyboard2/Keyboard2View.java
  Fn:   onTouch(), onPointerDown(), onPointerUp()
  Does: Maps pointer coordinates to KeyboardData.Key and delegates to _config.handler.
         |
         v
Step 7: Text commit
  File: srcs/juloo.keyboard2/KeyEventHandler.java
  Fn:   key_up() -> send_text(text)
  Does: Gets InputConnection from receiver and calls conn.commitText(text, 1).
```

## Additive snippet flow target

```text
FLOW: FrankenKey snippet insertion add-on

Step 1: IME view creation
  Source: Keyboard2.create_keyboard_view() after source import
  Plan: Keep baseline R.layout.keyboard / Keyboard2View behavior intact; add/wrap an upper snippet row without changing key hit-testing.
         |
         v
Step 2: Snippet row render
  Source: new FrankenKey snippet view/model files after proof
  Plan: Read local snippet slots, group into pages of seven, render horizontal left/right scroll/pager above baseline keyboard.
         |
         v
Step 3: Snippet tap
  Source: snippet row click handler after proof
  Plan: Resolve tapped slot phrase; do nothing for empty slot or open editor only if deliberately wired.
         |
         v
Step 4: Text commit
  Source: Keyboard2 receiver or small insertion helper after proof
  Plan: Use getCurrentInputConnection().commitText(phrase, 1), no trailing space, keep input view open.
         |
         v
Step 5: Baseline preservation
  Verification: normal Keyboard2View key touches still flow to KeyEventHandler.send_text unchanged.
```

## Boundary assumptions to prove before implementation

1. The upstream project can be imported with submodules and built in this environment.
2. Adding a row above `R.layout.keyboard` can preserve `Keyboard2View` measurement/hit-testing and input-window behavior.
3. Snippet insertion can reuse `InputConnection.commitText(phrase, 1)` without interfering with `KeyEventHandler` modifier/autocap state.
4. Local snippet persistence can be direct preferences/database storage without direct-boot crashes in settings or IME service.
5. Package/app identity can be changed to FrankenKey without breaking generated resources, manifest metadata, or debug signing.

## Focused verification path

- Unit proof/test: label generation for empty/one-char/two-plus-char phrases.
- Unit proof/test: group snippets into pages of seven.
- Unit proof/test or small instrumentation seam: insertion helper calls `commitText(phrase, 1)` exactly and does not append spaces.
- Build verification: Gradle debug APK after SDK/tooling prerequisites are resolved.
- Runtime smoke: enable/select FrankenKey, type baseline keys, add >7 snippets, scroll to the second group, tap a snippet, verify exact inserted phrase and keyboard remains open.
