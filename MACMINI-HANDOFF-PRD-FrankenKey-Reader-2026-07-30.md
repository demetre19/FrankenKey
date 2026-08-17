# Mac mini Handoff PRD: FrankenKey Reader

Date: 2026-07-30
Status: Approved and authorized for build
Approval phrases received: `APPROVE PRD`, `START BUILD`

## Objective

Complete the approved FrankenKey Reader work entirely from the Mac mini source checkout. The Mac mini is the build and orchestration host. Do not run the implementation controller on the Android device.

The approved product contract is:

`/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey/PRD-FrankenKey-Reader-2026-07-30.md`

It defines three sequential releases:

1. Clipboard Reader with shared Android text-to-speech playback, IME and clipboard actions, a dedicated Reader screen, local-first voice controls, privacy handling, and lifecycle restoration.
2. Reader Library with app-private local storage, Android sharing, bounded text import, safe URL import, non-DRM EPUB, and text-layer PDF support. Scanned or image-only PDFs must report that OCR is required; OCR is out of scope.
3. Explicit opt-in AccessibilityService “Read Current Page” with disclosure, bounded traversal, sensitive-node exclusion, navigation, revocation, and safe fallback to Share to Reader.

## Mac mini Paths

- Delivery root: `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey`
- Android source: `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect`
- Approved PRD: `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey/PRD-FrankenKey-Reader-2026-07-30.md`
- Durable AutoBuild run: `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect/.prd-runs/frankenkey-reader/20260730T015226Z`
- Controller instructions: `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect/.prd-runs/frankenkey-reader/20260730T015226Z/controller-execution-prompt.md`
- Controller log: `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect/.prd-runs/frankenkey-reader/20260730T015226Z/logs/resident-controller.log`

## Current Durable State

- Graph revision: 2
- Tasks: 30
- Dependencies: 35
- `G001` is complete with `evidence/G001.md`.
- `P001` was recovered from a stalled laptop-tethered controller and is `reopened`.
- The remaining 28 tasks are pending behind the proof task.
- The previous laptop-tethered controller was stopped.
- A `task:` handoff containing these paths and requirements was queued successfully through `msg mini`.

Resume from `P001`; do not redo `G001` unless its evidence is invalid.

## Required Mac mini Execution

1. Read this handoff, the approved PRD, the run’s `context.md`, `controller-handoff.md`, `todo.csv`, `dependency-tree.csv`, and proof/access artifacts.
2. Read the source-root `AGENTS.md` and every applicable child `AGENTS.md` before editing each target path.
3. Inspect the durable run database before acting. Record every task transition and evidence artifact in the AutoBuild run; do not create a parallel chat-only build.
4. Continue in graph dependency order from `P001` through `SIGN001`.
5. Preserve all pre-existing source changes. Never discard user work or treat generated dirty `bin/` files as permission to reset the checkout.
6. Use the existing native Android Java/XML architecture. Do not substitute a web app, cloud service, OCR pipeline, hidden accessibility enrollment, placeholder, stub, or mock delivery path.
7. Run focused tests for each changed contract, then smoke-test the actual paths. Use Java 17 for Gradle.
8. Build only signed release APKs. Run `verifyReleaseIdentity` and inspect package/version, signature, size, and SHA-256.
9. Replace `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey/FrankenKey-installable-release.apk` with the verified final signed APK before calling the build ready for user testing.
10. Do not install the APK, push commits, create a GitHub Release, publish files, or perform public uploads without later explicit approval.
11. The Android phone is not the compute or orchestration host. Any required final APK copy to the phone is a release-delivery gate only after the Mac mini build is complete; installation remains prohibited.
12. Complete recursive review, adversarial review, DOX closeout, and final AutoBuild signoff with durable evidence.

## Resume Commands on the Mac mini

Inspect current state:

```sh
RUN_DIR=/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect/.prd-runs/frankenkey-reader/20260730T015226Z
/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/AutoBuild/.venv/bin/python \
  /Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/AutoBuild/prd_runner.py \
  controller-status --run-dir "$RUN_DIR" --json
```

Resume with the prepared Mac mini controller instructions:

```sh
PATH=/Users/apple/.local/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin \
/Users/apple/.local/bin/omp \
  --cwd=/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect \
  --approval-mode=yolo \
  --thinking=high \
  @/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/FrankenKey-autobuild-autocorrect/.prd-runs/frankenkey-reader/20260730T015226Z/controller-execution-prompt.md
```

## Completion Criteria

The handoff is complete only when:

- all approved Release 1, Release 2, and Release 3 behaviors are implemented;
- every AutoBuild task through `SIGN001` has valid durable evidence;
- focused tests and real changed-path smoke tests pass;
- the signed release build passes `verifyReleaseIdentity`;
- the canonical delivery APK is the exact verified final APK;
- reviews and DOX closeout are complete;
- no installation or publication occurred without explicit approval.
