# PRD: FrankenKey Typing Intelligence — Section E: Offline Grammar Rules and AI "Fix grammar"

Date: 2026-09-27 (launch 2026-09-28)
Status: **Approved** (both features, 2026-09-27); Q5 default adopted (separate `ti_ai_writing_model` defaulting to the Reader AI model). Launch authorized 2026-09-28.
Source repo: `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect`, base `autobuild/frankenkey-autocorrect-suggestions` @ `57f1356`.

## 1. Goal

1. Deterministic, high-precision, on-device grammar checks (`your welcome`, `could of`, `a apple`, `the the`, `its a`, `rather then`) offered as one-tap fixes in the assistant strip — never applied silently.
2. An explicit omnibutton `Fix grammar` action that sends the field text or selection to the user's OpenRouter model after the v4 disclosure, previews a word-level diff, and replaces only after revalidation.

## 2. Canonical specification

`plan/PRD-FrankenKey-Typing-Intelligence-2026-09-27/E-Grammar.md` is binding (E-F1 through E-F4, stages E1–E5, rule tables, tests, DOX lines, changelog). Overview principles 2, 3, 7, 9; §4 policy changes; §7.4 eligibility bind.

## 3. Scope

- **E1** `GrammarRules` engine: pure/stateless/no-alloc-beyond-results; the v1 rule families (`DOUBLE_WORD`, `A_AN`, `COULD_OF`, `YOUR_YOURE`, `ITS_ITS`, `THEIR_THERE`, `THEN_THAN`, `ALOT`) with the narrowed high-precision forms from the lane table; word data in `assets/grammar/en.json`; ≥40 positive + ≥80 negative fixtures per family; a rule ships only at ≥98% negative precision and 100% positive.
- **E2** `GrammarCoordinator`: word-boundary trigger on a sliding ≤8-word window; system checker unchanged and merged (offline first, deduped); ≤3-issue queue; Fix/Ignore/Undo/⋯ prompt with per-rule switch; session-level ignore.
- **E3** `FIX_GRAMMAR` catalog action (unassigned by default): prose-editor eligibility with refusal toasts; ≤4,000-char input with selection-required when `ExtractedText` isn't provably complete; disclosure `v4`; `ti_ai_writing_model`; request via existing `ReaderAiOpenRouter` build/parse (temperature 0, 1.5× tokens, 20 s, cancellable); response sanitization + length-ratio guards.
- **E4** `GrammarDiffView` word-level diff panel + Replace/Copy/Cancel; SHA-256 snapshot revalidation; minimal back-to-front segment edits in one batch (untouched rich spans survive); mid-apply mismatch rolls back; 5 s Undo.
- **E5** emulator proof + live smoke only with separate user authorization.

## 4. Constraints

- Fully independent of every other lane; shared-file contact limited to `Keyboard2.java` (grammar section), `res/xml/settings.xml`, strings, `AGENTS.md` lines — line-disjoint, declared `shared_paths` in the run plan.
- No IME-action trigger, no auto-apply, no caching of requests/responses, nothing in the Reader AI saved library; only HTTP status + model id logged.
- The floating omnibutton shows the v1 unavailable message only.
- Never version bump / signed APK / phone upload — integrator/operator only.

## 5. Acceptance criteria

1. Fixture-driven rules green; casing preserved; offsets correct with emoji/surrogate pairs; code-like lines untouched; no overlap with existing decoder repairs (listed in lane report).
2. `your welcome␣` without final punctuation → prompt; no post-Send prompt; dedupe with system checker; queue ≤3; session ignore; 5 s Undo restores; revalidation failure drops the issue silently.
3. Rules on a 500-char sentence ≤2 ms p95.
4. AI: eligibility matrix honored; >4,000 chars or partial ExtractedText → selection required/refused; v4 disclosure gates the action while v3 covers the rest; no-key → AI settings; quotes/fences stripped; empty/identical/out-of-ratio responses → no Replace.
5. Replace: hash-mismatch → no edit; minimal edits preserve an unchanged styled span; mid-apply mismatch rolls back fully; Undo restores; nothing written to Reader AI caches/stores.
6. `grammar/AGENTS.md` created and indexed; `srcs/juloo.keyboard2/AGENTS.md` grammar + Reader AI bullets and the delivery `AGENTS.md` Reader AI line updated same-commit; `PRODUCT.md` sentence extended.

## 6. Changelog copy

Owned by `E-Grammar.md` §8 (integrator assembles at release).

## 7. Dependencies

Machinery lane `e-grammar` (no dependencies) in `plan/PRD-FrankenKey-Typing-Intelligence-2026-09-27/crew-plan-typing-intelligence-2026-09-27.json`.
