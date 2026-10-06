# fk-s1-requirements — S1 wave build-requirements audit

Date: 2026-10-06 · Host: MacBook-Pro.local (2IC) · Lane: `fk-s1-requirements` @ `fix/fk-s1-requirements`

## Fixes applied

| Fix | Where | Status |
|---|---|---|
| `sdk.dir=/Users/apple/Library/Android/sdk` | this worktree `local.properties` (created; gitignored) | done |
| same dead `/Volumes/TheHoneyBadger/AndroidTooling/android-sdk` | `FrankenKey-autobuild-autocorrect`, `SafeRec`, `SpeedyWatch` `local.properties` (disk-only, gitignored) | done |
| `backend_template` in `crew-plan-…crew.json` | FrankenKey repo — landed by boss on `fix/crew-json-backend-template` @ `c3d3931` (valid JSON, all 8 lanes intact; reindent-only churn) | done |

Note: `FrankenKey` checkout working tree also carries a pre-existing deletion of `crew-plan-…md` (untracked to this lane; not mine — preserved).

## Requirements audit — S1 lanes (a0-seams + e-grammar), `g1-unit-suite` gate

| # | Requirement | Status | Evidence |
|---|---|---|---|
| 1 | `./gradlew --no-daemon --no-configuration-cache tasks` resolves | GREEN | `BUILD SUCCESSFUL in 6s` after local.properties fix |
| 2 | `testDebugUnitTest` compiles+runs | GREEN with data | 571 tests run, **8 pre-existing failures**: 6× `KeyEventHandlerAutocorrectContractTest` (autocorrect/backspace contract), `ReleaseUpdaterResourcesTest.release_metadata_is_2_0_79_version_code_130` (version drift), `LanguagePackManagerTest.bundledEnglishPacksLoadDictionaryAndContextResources`. Gate `expected_exit:0` will read FAILED on base — lanes need `--prove-base-red`/exemption or a base-fix lane |
| 3 | JDK | GREEN | JDK 17 required by project; `gradle -version`: Launcher JVM 17.0.20.1 (Homebrew), `JAVA_HOME=/opt/homebrew/opt/openjdk@17` needed in gate env |
| 4 | `python` on PATH | BLOCKED→worked-around | `compileComposeSequences` execs bare `python` (build.gradle.kts:186 etc.); no `python` binary on host (`~/.local/bin/python` missing, only `python3`). Workaround proven: `$TMPDIR/fkbin/python→python3` symlink in PATH makes suite run. Real fix needs host-level `~/.local/bin/python` symlink (fenced for me) or build change |
| 5 | `ANDROID_HOME`/`ANDROID_SDK_ROOT` | N/A for g1 | unset in env; `sdk.dir` in local.properties suffices for gradle tasks |
| 6 | Emulator/adb for later waves | GREEN | `~/Library/Android/sdk/emulator/emulator`, `platform-tools/adb` present; AVDs: `fkTest_API36`, `SpeedyWatch_API_36`; system-images android-36 |
| 7 | debug.keystore | GREEN | `~/.android/debug.keystore` present (2618 B) |
| 8 | Network/caches | GREEN | `~/.gradle/caches` 1.8 G, deps cached — suite ran fully offline of wrapper-download |
| 9 | Disk space | GREEN | 91 Gi free on `/` |
| 10 | compileSdk | GREEN | compileSdk android-36 present in `~/Library/Android/sdk/platforms/` |

## S1 lane plan files

EXIST: `FrankenKey/plans/prd-frankenkey-typing-intelligence-2026-09-27/{S1,S2,S3}.plan.json`, `roadmap.json`, `VER-lane-template.json`, `AUD-fk-watch.plan.json`.
S1.plan.json lanes: `a0-seams`, `e-grammar`, `ver-s1` (deps: a0/e none, ver-s1←e-grammar). `backend_template` in S1.plan.json already fixed (live path).

## Defect for boss — wrong base_sha

`S1.plan.json.base_sha = ef9c2198…` is a **plan-repo sha** (`plan: bind acceptances…`, FrankenKey repo), but `repo` = `FrankenKey-autobuild-autocorrect`. `ef9c219` is NOT an ancestor of `autobuild/frankenkey-autocorrect-suggestions` (tip `e061034`, 2.0.106). Old crew.json `base_sha=57f1356` (2.0.119) is also not an ancestor of that branch — the real S1 base needs a boss decision: `e061034` (current branch tip), `631e802` (init-repo on prd/init-20261006), or a re-based plan sha. Queue entry for the full crew plan already sits `failed` in `ledger/queue/FrankenKey/2IC/`.
