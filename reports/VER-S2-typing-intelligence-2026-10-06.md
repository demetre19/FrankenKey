# VER S2 — strip reliability + power-store + adaptive learning (merged wave)

Merged tip under test: `358a2b7936efd80540042d0178f568eda6c36b01` (lane branch tip; all S2 build lanes landed beneath it).
Environment: `sdk.dir=/Users/apple/Library/Android/sdk`, `JAVA_HOME=/opt/homebrew/opt/openjdk@17`. No `/Volumes/*` dependency required.

## Gate receipts

| Gate | Command | Exit | Evidence |
|---|---|---|---|
| g1-unit-suite | `./gradlew --no-daemon --no-configuration-cache testDebugUnitTest` | 0 | `receipts/g1-unit-suite.json` (PASS) |
| assembleDebug | `./gradlew --no-daemon --no-configuration-cache assembleDebug` | 0 | BUILD SUCCESSFUL in 47s, 47 tasks executed; `build/reports/problems/problems-report.html` |

Unit suite: exit 0 — the 6 persistent reds called out in the a-stability re-brief (CleanModeFleksyLayoutTest, KeyboardLayoutSeamTest, LanguagePackManagerTest, LauncherPrivacyCardTest, ReaderAiButtonTest, ReleaseUpdaterResourcesTest) were resolved by the landed a-haptic fix wave (commit `5527def`); no failing test ids remain on the merged tip.

## Build-lane acceptance spot-checks

- **a-stability** (CandidatesView/SharedDecoder strip reliability): suggestions-package tests green including 3 new §7 stability tests; READY-item retention, 150ms/70% dim, slot-change clear verified by `c32ac02` gate.
- **a-haptic** (haptic/feedback strip work): landed with gate fixes at `5527def` — snippet_row ordering, omnibutton tracking, release metadata unpin, ReaderAiButtonTest foldable flag.
- **a-ticket** (adaptive learning / CandidateTicket): content-validated ticket acceptance, retained-result ring, legacy fallback all landed (`2e6a639`–`1910266`); ticket tests green inside suite.
- Power-store: storage smoke covered by suite receipts; no separate device step required (no emulator reachable is not a blocker — no gradle step needed one).

## VERDICT

**VERDICT: GO** — unit suite exit 0 and assembleDebug exit 0 on merged tip `358a2b7`; no wave-blocking defects.
