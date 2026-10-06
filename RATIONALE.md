

## 2026-10-06 — fix-grammar-dead-enum plan.json

VER-S1 defect 1: srcs/juloo.keyboard2/ReaderAiAction.java FIX_GRAMMAR enum is dead code — nothing references the enum; the strip button wires fix_grammar_action directly. Either wire it through the omnibutton action system or delete the file. On autobuild tip 9229020 (branch autobuild/frankenkey-autocorrect-suggestions). When done: crew learn and cite.


## 2026-10-06 — feat-grammar-rule-disable-ui plan.json

VER-S1 defect 2: the grammar strip dismiss path calls show_grammar_issue_options() which only does session-scoped ignoreShowing() — Presenter.disableRule / disableShowingRule() and the ti_grammar_rules_off persistence in Keyboard2 exist but are unreachable from UI; doc comment overclaims ('offers ignore-once or a persisted per-rule disable'). Add a long-press or options affordance so a user can persistently disable a rule, or soften the comment. On autobuild tip 9229020. When done: crew learn and cite.


## 2026-10-06 — fix-upstream-reds-2-0-118 plan.json

autobuild tip 41b623e carries 6 pre-existing upstream unit-test reds that predate S1 (introduced by release 2.0.118 / upstream drift, confirmed by boss full-suite run on /tmp/ver-s1-recheck): (1) CleanModeFleksyLayoutTest.keyboard_layout_keeps_snippet_row_above_keyboard_view and (2) KeyboardLayoutSeamTest.snippet_row_is_direct_sibling_immediately_above_keyboard_view — 4b14c71 reordered res/layout/keyboard.xml so reader_transport_strip sits between SnippetRowView and Keyboard2View; decide whether the reorder is intentional (update tests to accept transport between snippet and keyboard) or revert it. (3) LauncherPrivacyCardTest.shortcut_map_button_opens_the_complete_compact_guide — modal must document the AI button tap (expected text missing). (4) ReaderAiButtonTest.config_loads_omnibutton_edge_swap — NPE foldableUnfolded null (config loader drift). (5) ReleaseUpdaterResourcesTest.release_metadata_is_2_0_108_version_code_159 — version pin stale vs current 2.0.106+/157+; update pin or unpin. (6) LanguagePackManagerTest.bundledEnglishPacksLoadDictionaryAndContextResources — en_AU context prior mismatch (bundled next_words.tsv data drift). Fix each red so testDebugUnitTest is green; when done crew learn and cite.
