# VER-S1-typing-intelligence-2026-10-06 (2026-10-06)

Durable findings stream — written by `crew report append` (R12); never held in memory until the end.docs: machinery index missing — `crew docs build` generates it

## Verdict

VERDICT: NO-GO — `testDebugUnitTest` red on the merged tip (594 tests,
8 failures, 3 classes); `assembleDebug` green. See "Executed gates"
for receipts and failing test ids. The static seam review below still
stands; the defects section adds the suite reds, which are lane
defects, not park events.

## Verified (static)

### Seam correctness

- `Keyboard2.java` imports all five new `juloo.keyboard2.grammar` types;
  `Toast`, `AlertDialog`, `EditorInfo`, `InputConnection` imports
  present. No conflict markers anywhere in the diff.
- `GrammarCoordinator` presenter callbacks wired in `onCreate`:
  `present → grammar_issue_presented`, `apply → apply_offline_grammar_fix`,
  `undo → undo_offline_grammar_fix`, `disableRule → ti_grammar_rules_off`
  SharedPreferences persist + toast. Persisted disables re-loaded into
  `disabledRules()` on startup.
- `GrammarData.load("grammar/en.json")` runs on a background thread,
  result posted via `_handler` — no main-thread decode/wait, satisfies
  the bounded-async-decoder contract.
- `onUpdateSelection` pumps `pump_offline_grammar` only when cursor
  advanced (`newSelStart > oldSelStart`, collapsed); pump itself
  150 ms-debounced and gated on a word-boundary char — matches the
  "word boundary only" design.
- `finish_input_session` fully resets: coordinator `resetSession`,
  undo banner callback removed, diff view hidden, strip cleared.
- `ReaderAiOpenRouter` widened `final class → public`, `Message`
  public, `generate(apiKey, body, timeoutMs)` overload +
  parameterized `buildRequest(modelId, messages, maxTokens,
  temperature)` — required for `AiGrammarFixer` in the `grammar`
  subpackage (access verified: `ReaderAiSettings` stays package-private
  and is only touched by `Keyboard2`, same package).
- `ReaderAiSettings`: `getWritingModelId` falls back to Reader-AI
  model when `ti_ai_writing_model` unset; v4 disclosure flag separate
  from Reader's v3 — correct, Fix-grammar discloses its own flow.
- `GrammarDiffView.applyCorrection`: applies aligned segments
  back-to-front with per-segment revalidation, rolls back applied
  segments on mismatch, restores cursor; `Keyboard2.grammar_diff_replace`
  additionally revalidates the whole snapshot via SHA-256 before
  applying — the changed-under-you case shows `grammar_fix_changed`
  toast and never corrupts editor text.
- `fix_grammar_action` guard order: editor-class refusal → missing
  key → disclosure → capture refusal → worker thread. `generate`
  timeout 20 s; `client.cancel()` called after success.
- `gradle.properties` sets `org.gradle.java.home=/opt/homebrew/opt/openjdk@17`
  — matches AGENTS.md JAVA_HOME; `pythonExe` fallback chain
  (`PYTHON` env → `~/.local/bin/python` → `python` → `python3`)
  covers the CI-PATH trap for all six Exec tasks (genEmojis,
  genLayoutsList, genMethodXml, checkKeyboardLayouts,
  compileComposeSequences).

### Settings render (smoke-check equivalent, static)

- `res/xml/settings.xml` adds `ti_grammar_offline_rules` (Switch,
  default true) and `ti_ai_writing_model` (EditText) inside the
  typing-assistance group; all new string refs
  (`pref_grammar_offline_*`, `pref_ai_writing_model_*`) resolve in
  `res/values/strings.xml`.
- `SettingsUiContractsTest` row pins `ti_grammar_offline_rules` →
  exact summary text — catches a missing/renamed key at test time.
- `res/layout/grammar_diff_view.xml` is a self-contained
  `GrammarDiffView` root, `visibility=gone` initial, ids match
  `GrammarDiffView.onFinishInflate` lookups (`grammar_diff_replace`/
  `copy`/`cancel`/`text`/`header`).
- `reader_transport_strip.xml`: `reader_transport_fix_grammar` button
  present; `ReaderActivityTest` asserts the view exists; `Keyboard2`
  wires it via the new 3-arg `wire_reader_quick_shortcuts`
  (2-arg overload retained → no caller breakage).

### Grammar engine

- `GrammarRules.check` bounded at `MAX_LENGTH=500` UTF-16 units;
  8 rules with id constants, precision fixtures exist for all 8
  (16 TSVs under `test/resources/grammar/`); `GrammarRulesTest`
  gates 100% positive / ≥98% negative per rule.
- `GrammarCoordinator`: 8-word window, ≤3 queued issues, session
  ignores in-memory only, dedupe vs system checker on
  (offset,length,replacement) — all constants match `grammar/AGENTS.md`.
- AI prompt asset `assets/grammar/ai_prompt.txt` is a strict
  preserve-everything correction prompt; `en.json` carries the a/an
  exception tables the rules consume.

## Defects found

1. **`ReaderAiAction` enum is dead code** — `FIX_GRAMMAR` is defined
   (`srcs/juloo.keyboard2/ReaderAiAction.java`) but nothing references
   the enum; the strip button wires `fix_grammar_action` directly.
   Either wire it through the omnibutton action system or delete the
   file. Mechanical fix — dispatchable.
2. **No UI path to per-rule disable** — the strip's dismiss action
   calls `show_grammar_issue_options()` which only calls
   `ignoreShowing()` (session-scoped). `Presenter.disableRule` /
   `disableShowingRule()` and the `ti_grammar_rules_off` persistence
   in `Keyboard2` exist but are unreachable from the UI; doc comment
   says dismiss "offers ignore-once or a persisted per-rule disable" —
   overclaims. Needs a long-press/options affordance or a softened
   comment.

Minor non-blocking note: `GrammarDiffView.diffOps` (public static)
appears unreferenced — thin wrapper over `diffWords`.

## Executed gates

Environment: `ANDROID_HOME=/Users/apple/Library/Android/sdk`,
`JAVA_HOME=/opt/homebrew/opt/openjdk@17`, merged tip `9d042cc`
(branch `lane/2IC/fk-typing-s1-20261006-061255/ver-s1`).

1. `./gradlew --no-daemon --no-configuration-cache testDebugUnitTest`
   — exit 1, BUILD FAILED in 59s. 594 tests, 8 failures:
   - `KeyEventHandlerAutocorrectContractTest` (6): all at
     `awaitCounts` line 1574, personalization counts stayed 0/0:
     `changed_candidate_and_autocorrect_backspace_accepts_correction`
     (the←teh), `accepted_thys_to_thus_then_manual_this_uses_corrected_source`
     (thus←thys), `literal_boundary_records_once_after_empty_word_key_rollover`
     (cazoo), `pending_replacement_never_blindly_undoes_after_cursor_or_suffix_change`
     (the←teh), `changed_candidate_and_autocorrect_commit_once_on_next_action_or_finish`
     (the←teh), `secondary_replacement_backspace_accepts_new_target`.
     Common signature: personalization counts never recorded —
     the personalization/learned-word sink never observed a commit.
   - `LanguagePackManagerTest.bundledEnglishPacksLoadDictionaryAndContextResources`:
     "en_AU must preserve the decisive observed context prior."
   - `ReleaseUpdaterResourcesTest.release_metadata_is_2_0_79_version_code_130`:
     "The release must be versionName 2.0.79." — version pin stale
     vs current 2.0.106 (pre-existing pin, unlikely a merge
     regression).
   Receipts: `build/test-results/testDebugUnitTest/TEST-*.xml`.
2. `./gradlew --no-daemon --no-configuration-cache assembleDebug`
   — exit 0, BUILD SUCCESSFUL in 42s. APK packaged.
3. Device/emulator smoke (strip prompt, Fix-grammar disclosure →
   diff → replace → undo) not executed — no device attached; the
   settings-render and wiring equivalents are covered in the static
   section above.

## Verdict (final)

VERDICT: NO-GO. assembleDebug green but the unit suite is red on the
merged tip: 6 personalization-count failures in
`KeyEventHandlerAutocorrectContractTest` plus 2 stale-pin/data
failures. Lane defects to fix, not park events.

## Deferred to coordinator gates

- Real-device smoke: strip prompt on "your welcome" boundary, Fix
  grammar disclosure → diff view → replace → undo — requires a
  running emulator/device session (not run; no device attached).

## Evidence

- Diff reviewed: `git diff e061034..HEAD` (lane base → HEAD c06eac9).
- Files inspected: `Keyboard2.java` (full +459-line hunk),
  `grammar/GrammarRules.java`, `grammar/GrammarCoordinator.java`,
  `grammar/AiGrammarFixer.java`, `grammar/GrammarDiffView.java`,
  `grammar/GrammarIssue.java`, `grammar/GrammarData.java`,
  `ReaderAiSettings.java`, `ReaderAiOpenRouter.java`,
  `ReaderAiAction.java`, `res/xml/settings.xml`,
  `res/values/strings.xml`, `res/layout/grammar_diff_view.xml`,
  `res/layout/reader_transport_strip.xml`, `build.gradle.kts`,
  `gradle.properties`, `test/juloo.keyboard2/{GrammarRulesTest,
  GrammarCoordinatorTest,SettingsUiContractsTest,ReaderActivityTest}.java`,
  `assets/grammar/{en.json,ai_prompt.txt}`, `grammar/AGENTS.md`.
