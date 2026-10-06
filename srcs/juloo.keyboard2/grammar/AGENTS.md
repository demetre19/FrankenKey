# grammar/ DOX

## Purpose

- Own the offline grammar rules engine, the boundary-driven coordinator, the AI "Fix grammar" flow, and the word-level diff preview.

## Ownership

- `Keyboard2` owns presentation and editor mutation; `assets/grammar/` owns word data and the fixed system prompt; `ReaderAiSettings`/`ReaderAiOpenRouter` own the encrypted key and transport.

## Local Contracts

- `GrammarRules` is pure and stateless: one bounded sentence (≤500 UTF-16) in, ordered `GrammarIssue` list out, offsets in UTF-16 with casing preserved from `GrammarData`.
- `GrammarCoordinator` runs on the IME main thread at word boundaries only, on a sliding ≤8-word window, merges with the system checker via (offset, length, replacement) dedupe, and keeps at most 3 queued issues. Session ignores never persist; per-rule disables persist in `ti_grammar_rules_off`.
- Prompt UI offers "matched" → "replacement" with Fix/Ignore; a successful Fix offers a 5 s Undo that restores the original span. Nothing is decoded or waited on the main thread beyond the bounded pure check.
- The AI Fix grammar action is explicit-tap only: prose-only eligibility with refusal toasts for password/numeric/phone/email/URI/terminal/unreadable fields; input is the selection else a provably-complete whole field bounded at 4,000 UTF-16; first use requires the v4 disclosure; requests are temperature 0 with ~1.5× max_tokens and a 20 s cancellable timeout through `ReaderAiOpenRouter`.
- Responses pass quote/fence stripping plus empty/identical/0.5×–1.5×-ratio guards before preview. `GrammarDiffView` renders struck-through removals and accent additions, and Replace applies minimal back-to-front segment edits inside one batch edit so untouched rich spans survive; a mid-apply mismatch rolls back every applied segment and reports the text changed. Nothing is cached, saved, or logged beyond HTTP status and model id; `NO_PERSONALIZED_LEARNING` fields show the private-typing header.

## Work Guidance

- Keep engines free of Android UI dependencies; presenter and view code stay in `Keyboard2`/`GrammarDiffView`. Add rules only with positive and negative fixtures under `test/resources/grammar/` holding ≥98% precision.

## Verification

- `GrammarRulesTest`, `GrammarCoordinatorTest`, and the diff/rollback cases are the contracts; run them plus a compile of `Keyboard2` for wiring changes.
