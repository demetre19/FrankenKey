# PRD: FrankenKey Typing Intelligence — Section B: Email Address Memory and `@` Domain Suggestions

Date: 2026-09-27 (launch 2026-09-28)
Status: **Approved** — all step-2 behaviors approved by the operator 2026-09-27; Q4 default adopted (prose addresses remembered as a lower-weight source). Launch authorized 2026-09-28.
Source repo: `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect`, base `autobuild/frankenkey-autocorrect-suggestions` @ `57f1356`.

## 1. Goal

Email addresses the user types are remembered on this phone and offered again after a couple of letters. Typing `@` offers the user's domains plus common providers (`gmail.com` first). Email text is never autocorrected.

## 2. Canonical specification

`plan/PRD-FrankenKey-Typing-Intelligence-2026-09-27/B-Email-Memory-and-Domains.md` is binding (B-F1 through B-F9, stages B1–B5, frecency formula, tests, DOX lines, changelog). Overview principles 5–8, §4 policy changes, §7.2 storage, §7.4 editor eligibility bind.

## 3. Scope

- `EmailToken` parser and `EmailAddressValidator` (B-F1/B-F2); mentions `@x`, mid-token cursors, double `@`, unicode all handled per spec.
- `EmailMemoryStore`: `email_memory` named prefs in credential-protected storage, JSON v1, 200-entry frecency cap, direct-boot unavailable-then-retry, lazy load on the `SharedDecoder` worker only.
- `EmailLearningObserver` events (B-F4) with the field-finish, prose-completion, and selection sources; never in sensitive/structured editors.
- `EmailCandidateSource` (B-F5): email-field recall, prose slot-3 recall ≥3 chars, post-`@` domain candidates (user domains then built-ins, cap 6, exact-domain hidden); ≤1 ms budget.
- Acceptance handlers (B-F6): domain/address replacement with verify-then-batch-edit; never a trailing space; no C-learning or D-calibration evidence.
- B-F7 autocorrect suppression inside email tokens and email fields (failing test first).
- Settings (B-F8) + Emails tab + clear-all + backup exclusions (`backup_rules.xml`, `data_extraction_rules.xml`, `SettingsBackup` non-export).
- Soft email-field detection via strict hint regex (B-F9).

## 4. Constraints

- Requires A0 seams (CandidateSource, SuggestionAcceptor, ticket) before B3; B1–B2 logic is dependency-free — the machinery lane starts after `a0-seams` merges, and the worker orders B1→B5 inside its stage list.
- All reads/writes on the decoder worker; `apply()` writes debounced 2 s.
- Never stored: contact emails (C6 reads names only), autofill integration, network domain validation.
- Never bump version, build signed APK, or upload — integrator/operator only.

## 5. Acceptance criteria

1. `name@gmial.` typed in an email field and in prose stays byte-identical (no autocorrect), likewise `first.last@company.co.uk` and `a+b@x.io`.
2. Empty email field shows up to 3 saved addresses on page 1, more on page 2; prefix narrows by local part or full address, case-insensitive.
3. `demetre@g` offers `gmail.com`; selecting a domain or address never appends a space.
4. Prose: `@x` with a non-empty local part shows domain candidates; bare `@name` (mention) shows none; saved-address recall appears only in slot 3 for ≥3-char tokens.
5. Store: 201st entry evicts lowest frecency; corrupt JSON → empty store, rewritten on next save; unreadable before first unlock without throwing, works after `ACTION_USER_UNLOCKED`.
6. Backup: `email_memory.xml` excluded in `backup_rules.xml` and both `data_extraction_rules.xml` domains; `SettingsBackup` export contains no `email_memory` (asserted by test).
7. Settings copy shows both switches with their explanations; turning Remember off stops learning/recall and offers Keep/Delete.
8. Learned Words → Emails tab lists/search/deletes addresses; "Clear typing data" clears them.
9. `srcs/juloo.keyboard2/AGENTS.md` gains the email-memory exception line; `suggestions/AGENTS.md` gains `email/` ownership — same commit.

## 6. Changelog copy

Owned by `B-Email-Memory-and-Domains.md` §12 (integrator assembles at release).

## 7. Dependencies

Machinery lane `b-email` (depends_on `a0-seams`) in `plan/PRD-FrankenKey-Typing-Intelligence-2026-09-27/crew-plan-typing-intelligence-2026-09-27.json`. Replay scripts (B5) land after `a-strip`'s A4 harness.
