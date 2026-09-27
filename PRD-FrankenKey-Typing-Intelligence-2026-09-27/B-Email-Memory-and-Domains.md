# Lane B Handoff PRD — Email Address Memory and `@` Domain Suggestions

Date: 2026-09-27
Lane: B
Status: All step-2 behaviors approved by the user (2026-09-27). Open question Q4 (overview §3) has a default.
Depends on: A0 for B3 onward. B1–B2 have no dependency and can start immediately.
Parallel with: A1–A4, C, D, E (all stages).
Read first: `00-Overview-and-Philosophy.md` (principles 5–8; §4, §7.2, §7.4).

## 0. Handoff essentials

| Item | Value |
|---|---|
| Source checkout (Mac mini) | `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect` |
| Base / branch / worktree | `57f1356` / `ti/lane-b` / `../FrankenKey-ti-b` |
| DOX chain | source `AGENTS.md` → `srcs/AGENTS.md` → `srcs/juloo.keyboard2/AGENTS.md` → `suggestions/AGENTS.md`; `res/AGENTS.md`; `test/AGENTS.md` |
| New files | `suggestions/email/EmailToken.java`, `EmailAddressValidator.java`, `EmailMemoryStore.java`, `EmailCandidateSource.java`, `EmailLearningObserver.java`, `LearnedEmailsTab.java`; `res/values/email_domains.xml` (+ `values-en-rAU`, `values-en-rGB`, `values-en-rUS`) |
| Touched files | `EditorConfig.java` (new `should_use_email_memory`), `KeyEventHandler.java` (acceptance handler registration, finish-input hook), `Keyboard2.java` (finish-input callback), `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `SettingsBackup.java` (make sure it is not exported), `res/xml/settings.xml` (Smart typing → Email), strings |
| Tests | new `test/juloo.keyboard2/suggestions/email/*Test.java`; extend `EditorConfigTest`, `SettingsBackupTest`, `LearnedWordsActivityTest`; replay scripts in `ImeReplayInstrumentedTest` (after A4) |

## 1. Objective

Email addresses you type are remembered on this phone and offered again after a couple of letters. Typing `@` immediately offers the domains you use most and common providers (`gmail.com` first), so an address takes a few taps instead of twenty keystrokes. Email text is never autocorrected.

## 2. Current behavior (2.0.119)

- Email and URI fields are classed as structured (`EditorConfig.java:183-197`) and never learn.
- `CurrentlyTypedWord.is_word_char` accepts only letters, digits, and `'` (`CurrentlyTypedWord.java:455`). After `@`, the current word is empty. Only the part after the last `.` is decoded.
- Nothing stores or suggests email addresses or domains.
- `SettingsBackup` exports **all** default SharedPreferences (`SettingsBackup.java:92-111`). Storing addresses there would leak them into exported backups. That's why this lane uses a separate named file.

## 3. Scope

In scope:
- Email token parsing.
- Address validation.
- Local address memory, filled by learning events.
- Candidates that complete an address from its prefix.
- Domain candidates after `@`.
- Suppressing autocorrect inside email tokens.
- Settings switch with explanation.
- Learned Words → Emails tab.
- Clear-all integration.
- Backup exclusion.

Out of scope:
- Reading contacts' email addresses (C6 reads names only).
- Autofill-framework integration.
- Sync.
- Validating that a domain exists (no network).

## 4. User experience

**In an email field (for example the Gmail To field, a sign-in email field):**
1. Focus the field. The strip shows up to three most-used saved addresses (page 1), with more on page 2 (swipe left). They are ranked by frecency (§5.4).
2. Type `de`. The strip narrows to saved addresses whose local part **or** full address starts with `de` (case-insensitive).
3. Type `demetre@`. The strip shows saved full addresses that start with `demetre@` first, then domains: the domains you use (by frecency), then built-ins `gmail.com`, `outlook.com`, `icloud.com` on page 1 and `hotmail.com`, `yahoo.com`, plus the locale extra, on page 2.
4. Type `demetre@g`. The strip narrows to domains that start with `g`, so `gmail.com` appears.
5. Tap `gmail.com`. The field now reads `demetre@gmail.com`, and no space is added in email fields.
6. Tapping a saved full address replaces the whole token with that address.

**In a prose field (message, notes):**
- After you type `x@`, the domain suggestions appear the same way. Selecting one completes the domain and **adds a space** (prose).
- Saved-address recall in prose starts once the token has ≥3 characters and matches the start of a saved address. The best match appears in the **third** slot, so it never displaces the top word suggestion, and it is never auto-applied.
- A complete valid address followed by a space, comma, or Enter is remembered (source `PROSE`; Q4 default yes).

**Nothing is changed automatically.** Inside a token that contains `@`, there is no autocorrect, no auto-capitalization, no double-space period, and no `word.word` period conversion (the last is already the contract for email markers). The token is also excluded from C's learning and from D's touch-calibration training.

## 5. Functional requirements

### B-F1 Email token parsing (`EmailToken`)
- Input: `EditorContext.textBeforeCursor` (≤256) and `textAfterCursor` (≤64).
- Scan backward from the cursor until whitespace, start of text, or one of `,;<>()[]"'`, up to 254 UTF-16 units. If `textAfterCursor` begins with an address character, the token is "mid-token". In that case offer **no** email candidates, so text is never corrupted.
- Output: `{token, localPart, hasAt, domainPrefix, tokenStartOffset}`. `hasAt` is true only with exactly one `@`. A token with two `@` produces no candidates.
- Allowed local characters: `A–Z a–z 0–9 . _ % + - '`. Allowed domain characters: letters, digits, `-`, `.`. Anything else ends the email context.

### B-F2 Address validation (`EmailAddressValidator`)
- Local part 1–64 characters, from the allowed set; no leading, trailing, or consecutive `.`.
- Domain ≤253 characters, ≥2 labels; each label 1–63 characters of letters, digits, and `-`, with no leading or trailing `-`; the TLD is ≥2 letters.
- Total length ≤254.
- Addresses are normalized for storage as the NFC local part (case kept as typed) plus the domain lowercased.
- Rejected: IP-literal domains, quoted local parts, and anything containing whitespace.

### B-F3 Storage (`EmailMemoryStore`)
- Named SharedPreferences `email_memory` in **credential-protected** storage (not `DirectBootAwarePreferences`, so it can't be read before first unlock). Store it as one JSON string value, versioned (`v: 1`).
- Entry: `{address, count, firstSeenDay, lastUsedDay, sources: bitmask FIELD|PROSE|SELECTED}`.
- Maximum 200 entries. On overflow, evict the entry with the lowest frecency, ties broken by oldest `lastUsedDay`.
- Loaded once, lazily, **on the `SharedDecoder` worker**. All reads and writes happen on the worker (existing contract: personalization access is worker-confined). Writes are `apply()`, debounced 2 s.
- Excluded from Android backup: add `<exclude domain="sharedpref" path="email_memory.xml"/>` to `backup_rules.xml`, and the matching `<exclude>` to both `cloud-backup` and `device-transfer` in `data_extraction_rules.xml`.
- **Not** exported by `SettingsBackup`. It isn't in `NAMED_PREFS`; add a test that asserts it.
- Clear paths: the Emails tab (delete one or clear all) and "Clear typing data" (overview §7.2).

### B-F4 Learning events (`EmailLearningObserver`)
All learning requires `ti_email_memory_enabled` ON, `should_use_email_memory(info)` true, and **no** `IME_FLAG_NO_PERSONALIZED_LEARNING` flag.

| Event | Source | Count delta |
|---|---|---|
| Email field loses input (`onFinishInput`, `onStartInput` of another field, or the IME action Send/Next/Go/Done) and the whole field text (≤254) is one valid address | `FIELD` | +2 |
| In prose, a separator (space, `,`, `;`, Enter) completes a token that is one valid address | `PROSE` | +1 |
| The user taps an `EMAIL_ADDRESS` candidate | `SELECTED` | +2 |
| The user taps an `EMAIL_DOMAIN` candidate and the token becomes a valid address at field finish | handled by the `FIELD` row | — |

- The field text is read with `getExtractedText` (≤256 chars) on the main thread at finish time and handed to the worker. If it can't be read, nothing is learned.
- There is never any learning from password, numeric, phone, unknown, or terminal editors (overview §7.4).
- There is no learning from text the IME didn't see being typed or chosen. If a field was pre-filled and not edited during this focus, it doesn't count. Detection: the field text at focus start equals the text at finish → skip.

### B-F5 Candidate source (`EmailCandidateSource` via A0 `CandidateSource`)
Runs on the worker for every request whose `EditorContext` makes it eligible.

- **Before `@`**, in an email field: saved addresses whose full address or local part starts with the token (case-insensitive). An empty token lists the top 6 by frecency. In prose: only when the token is ≥3 characters, and only the top 1, placed in slot 3.
- **After `@`**, in any eligible field:
  1. saved full addresses that start with `token` (case-insensitive), at most 3;
  2. domains that start with `domainPrefix`, from the user's domains (distinct domains of saved addresses, ranked by the summed frecency of their addresses);
  3. then built-in domains in resource order, deduplicated.
  - Cap: 6 candidates in total across both strip pages.
  - Hide a domain that equals `domainPrefix` exactly (already complete).
- Roles: `EMAIL_ADDRESS` (full-address text) and `EMAIL_DOMAIN` (domain text; the strip label is the domain only, for example `gmail.com`).
- While the token is in an email context (`hasAt`, or an email field), the email source's candidates **replace** the ranked word candidates. In prose before `@`, they are merged as pinned at slot 3 only.
- Budget: ≤1 ms for 200 entries (linear scan is acceptable). A unit test guards the budget.

### B-F6 Acceptance (A0 `SuggestionAcceptor` handlers)
- `EMAIL_DOMAIN`: replace `domainPrefix` (the text between `@` and the cursor) with the domain, using `deleteSurroundingText(domainPrefix.length, 0)` + `commitText(domain)` in one batch edit, after verifying the text before the cursor still ends with `@` + `domainPrefix`. Add a space in prose; add nothing in email, URI, or search fields.
- `EMAIL_ADDRESS`: verify the text before the cursor ends with the token, then replace the whole token with the address (same verification and batch pattern). Count +2 (`SELECTED`).
- Both use the Lane A ticket, so exactly-once and reject-feedback rules apply.
- Neither creates C learning evidence (no unigram, bigram, or correction evidence) or D calibration samples.

### B-F7 Autocorrect suppression
- When the current token contains `@`, or the editor is an email field, the separator autocorrect path, late spellcheck repair, autocap, double-space period, and `word.word` conversion do not run on that token.
- Extend the existing email-marker checks in `KeyEventHandler`. Add regression tests for `name@gmail.com`, `first.last@company.co.uk`, and `a+b@x.io` in prose. In each case the text must stay byte-identical after space and after `.`.

### B-F8 Settings and management
- Smart typing → Email:
  - **Remember email addresses**, `ti_email_memory_enabled`, default **ON**. Summary: "Saves email addresses you type on this phone so you can re-enter them with a tap. Never backed up or shared."
  - **Suggest email domains**, `ti_email_domain_suggestions`, default **ON**. Summary: "After you type @, suggests the domains you use most, plus common ones like gmail.com."
- Turning *Remember* OFF stops learning and recall, and shows a dialog: "Keep saved addresses?" with **Keep** or **Delete**.
- Learned Words → **Emails** tab (A0 tab host):
  - alphabetical list with search;
  - each row shows the address plus "used N times";
  - swipe or long-press to delete one, with confirmation;
  - "Clear all emails", with confirmation;
  - no add or edit (addresses come from use).
  - Rows keep the ≥12dp inner text inset (`PRODUCT.md`).
- "Clear typing data" confirmation text adds "saved email addresses".

### B-F9 Editor eligibility (`EditorConfig.should_use_email_memory`)
- True for `TYPE_TEXT_VARIATION_EMAIL_ADDRESS` and `TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS`.
- Also true for normal text fields whose `EditorInfo.hintText` or `label` contains `email` or `e-mail` (case-insensitive; these fields are treated as email fields).
- Prose recall and learning after `@` (B-F4 `PROSE` row, B-F5) use the general typing-assistance eligibility.
- Always false for password variations, `TYPE_NULL`, and classes other than text.

## 6. Built-in domain resources

`res/values/email_domains.xml` defines the default order:

```xml
<string-array name="email_common_domains">
  <item>gmail.com</item><item>outlook.com</item><item>icloud.com</item>
  <item>hotmail.com</item><item>yahoo.com</item><item>proton.me</item>
</string-array>
```

The locale overrides replace the last one or two entries:
- `en-rAU`: `bigpond.com`, `optusnet.com.au`
- `en-rGB`: `hotmail.co.uk`, `btinternet.com`
- `en-rUS`: `aol.com`, `proton.me`

The locale comes from the active keyboard locale, not the system locale.

## 7. Implementation stages

| Stage | Content | Parallel? | Exit |
|---|---|---|---|
| **B1** | `EmailToken`, `EmailAddressValidator`, resource arrays; pure unit tests (≥60 cases: valid, invalid, mid-token, double `@`, unicode) | **Starts immediately**, no gate | Tests green |
| **B2** | `EmailMemoryStore`, `EmailLearningObserver`, `should_use_email_memory`, backup exclusions, finish-input hook, frecency (§8) | **Starts immediately**; `Keyboard2`/`KeyEventHandler` hooks are small and line-disjoint | Store, eviction, backup-exclusion, and learning-eligibility tests green |
| **B3** | `EmailCandidateSource` registration, acceptance handlers, autocorrect suppression | **After A0 merged** | Candidate/acceptance tests + suppression regression green |
| **B4** | Settings, Emails tab, clear-all integration | After A0 (tab host) | UI tests green; strings reviewed |
| **B5** | Replay scripts (email field and prose), emulator proof (Chrome sign-in page, a WebView email field, Messages) | After A4 | Replay green; screenshots in the lane report |

## 8. Frecency

`score = count × 0.5^(daysSince(lastUsedDay) / 90)`. Ties are broken by later `lastUsedDay`, then alphabetically. It's computed at query time with no stored score.

## 9. Tests (minimum)

- Parser: `de|`, `de@|`, `de@gm|`, `a,b@x|`, `<x@y|`, `x@y@z|` → none, a mid-token cursor → none.
- Validator: RFC-ish valid and invalid tables.
- Store: 201st insert evicts the lowest frecency; JSON round-trip; corrupt JSON → empty store with no crash, and the file is rewritten on the next save.
- Backup: `SettingsBackup.exportToJson` contains no `email_memory`; `backup_rules.xml` and `data_extraction_rules.xml` contain the exclusions (XML parse test).
- Learning: `FIELD` learning on finish; no learning when there's a no-personalized-learning flag, a password field, text that was pre-filled and unchanged, or the switch is OFF.
- Candidates: ordering (saved address > user domains > built-ins), cap 6, exact-domain hidden, prose slot-3 rule.
- Acceptance: domain completion in an email field (no space) and in prose (space); a stale ticket is rejected; text verification failure → no change and reject feedback.
- Suppression: the three prose examples stay byte-identical after space and after `.`.
- Clear typing data removes emails.

## 10. DOX and contract updates

- `srcs/juloo.keyboard2/AGENTS.md`: after "Structured/terminal fields remain stateless", add: "Exception: when *Remember email addresses* is on, eligible email fields and completed prose email tokens update a bounded (200), credential-protected, backup-excluded `email_memory` store used only for email completion. Email tokens are never autocorrected, capitalized, or learned as vocabulary."
- `suggestions/AGENTS.md`: add `email/` to Ownership, and add the email tests to Verification.
- `res/AGENTS.md` (if it lists backup rules): note the new exclusion.
- Root `README.md` feature list (integrator, at release): one line about email memory and domains.

## 11. Risks

| Risk | Mitigation |
|---|---|
| A sign-in field that's really "username", with an email-like hint | Learning only happens when the whole field text is a valid address; usernames aren't stored. |
| Privacy concern about stored addresses | Local only, excluded from backups and exports, visible and deletable, switch in Settings with a clear explanation. |
| Prose recall noise | ≥3-char prefix, slot 3 only, never auto-applied. |
| Web fields that read text lazily | Mid-token and verification checks refuse rather than corrupt; the reject feedback comes from Lane A. |

## 12. Changelog copy

- FrankenKey now remembers email addresses you type and offers them again after a couple of letters. It's stored only on this phone and never backed up.
- After you type @, it suggests the email domains you use most, plus common ones like gmail.com.
- Email addresses are never autocorrected or capitalized.
- Manage or delete saved addresses in Settings → Learned words → Emails.

## 13. Done definition

Stages B1–B5 have met their exit criteria, DOX is updated, the lane report lists files and tests, and the branch is merged by the integrator.
