# rebase-a0-seams — lane report

## Outcome

- Rebase of `lane/2IC/fk-typing-s1-20261006-061255/a0-seams` onto
  `57f1356955695a1cc586bd80def7a296b121a265` (autobuild tip) completed.
- New tip: `7731729899bfb3d6ab47e28fd4482a5d88599fcf` (7 commits).
- `git merge-base --is-ancestor 57f1356 HEAD` → OK.
- Pushed: `739e116 → 7731729` forced update on the lane ref.

## Conflict resolution

Single conflict: `srcs/juloo.keyboard2/SettingsActivity.java` in commit
`b75b95a` (→ `bd0403f`). Both sides were additive and disjoint:

- HEAD (target): `reader_ai_button` and `reader_page_capture` preference
  click wiring inside `setupTypingAssistancePreferences()`.
- Lane (b75b95a): `hideEmptySmartTypingCategories()` call plus the new
  method, and the `PreferenceScreen` import.

Resolved by keeping HEAD's block then appending the lane's call + method;
compiled clean (`compileDebugJavaWithJavac` green).

## Gate evidence

`./gradlew testDebugUnitTest`:

- Lane tip `7731729`: 569 tests, **6 failed** —
  CleanModeFleksyLayoutTest, LauncherPrivacyCardTest, ReaderActivityTest,
  ReleaseUpdaterResourcesTest, LanguagePackManagerTest,
  KeyboardLayoutSeamTest (one failing case each; see
  `build/test-results/testDebugUnitTest/`).
- Base `57f1356` (detached, same worktree): 569 tests, **12 failed** —
  a strict superset: the same 6 plus KeyEventHandlerAutocorrectContractTest
  and others.

Every lane failure is identical on the base → base-red, not introduced by
the rebase. The lane actually repairs 6 base failures.

## Machinery defect — `skip-worktree` breaks lane rebases

`src/crew/runops/scope.py::_apply_skip_worktree` (lines ~652–659) sets
`git update-index --skip-worktree` on every tracked file matching a
declared `protected_paths` pattern and not covered by the lane's grant
paths — here `build.gradle.kts`. skip-worktree makes `git checkout` /
`git rebase` refuse with "local changes would be overwritten" because the
file carries an index entry the checkout cannot update. Every rebase of a
lane worktree whose protected list covers a file the rebase must rewrite
will wedge the same way; the cleared flag was observed being set by
scope-install machinery, not by the lane itself. Suggested fix class:
apply skip-worktree only for enforcement of *writes*, and clear it before
any engine-driven checkout/rebase (or use an attribute/mechanism that
doesn't block checkouts, e.g. assume-unchanged has the same problem — the
real fix is a pre-rebase sweep that clears and re-applies).

## Stash note

`stash@{0}` ("a0-seams WIP — pre-rebase") holds tasks.json + lane
bookkeeping — lane scratch, intentionally left unpopped per brief.
