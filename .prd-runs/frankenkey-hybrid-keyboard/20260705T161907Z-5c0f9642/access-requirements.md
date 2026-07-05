# Access Readiness Gate

Run: `20260705T161907Z-5c0f9642`  
Updated: `2026-07-05T16:55:05Z`

## Credential and account requirements

No raw secrets are required for the approved PRD scope.

| Requirement | Purpose | Status | Notes |
|---|---|---|---|
| GitHub public access | Clone/read Unexpected Keyboard upstream and public dependencies | resolved | Public repository; no token required. |
| Gradle/Maven network access | Resolve Android Gradle Plugin and AndroidX/JUnit dependencies | resolved | Network access is available in the session. |
| Release signing credentials | Release APK signing | not required | PRD acceptance only requires debug APK; upstream debug signing can auto-create a debug keystore. |
| Fleksy APK folder | Behavioral reference | resolved | APK already supplied at `/Users/apple/Documents/UNCLUTTER-NEW/CLAUDE-DEV/Fleksy/fleksy-keyboard-11-0-0.apk`. |
| Cloud/API/SaaS accounts | Snippet sync, analytics, backend services | not required | PRD explicitly requires local-only snippets and no accounts/network handling for snippet content. |

## Environment prerequisites, not secrets

These are implementation/verification prerequisites rather than credentials:

| Item | Status observed | Work blocked if unresolved | Planned handling after `START BUILD` |
|---|---|---|---|
| Android SDK path | not detected in `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `/Users/apple/Library/Android/sdk`, or `/Applications/Android Studio.app`; external drive `/Volumes/TheHoneyBadger/` is available and approved for installs | Gradle Android build and APK creation | After `START BUILD`, install or locate Android Studio/SDK/tooling under `/Volumes/TheHoneyBadger/` rather than the internal disk. |
| FontForge | `fontforge` not found on PATH | Upstream font asset generation if required by Gradle tasks | Build-foundation task determines whether tracked assets avoid this; otherwise install/resolve FontForge under `/Volumes/TheHoneyBadger/` where practical before build. |
| Device/emulator | `adb devices` listed no attached devices | End-to-end install/typing smoke test | Build can proceed; final device smoke remains blocked until emulator/device is available, unless SDK/emulator setup succeeds after `START BUILD`. |

No user-facing secret handoff file is needed at this stage.

## User installation directive

The user explicitly approved installing Android Studio, Android SDK, or related tooling as needed, with installs placed on the external drive: `/Volumes/TheHoneyBadger/`. Do not install bulky Android tooling on the internal project disk unless no external-drive-compatible path exists and the reason is recorded.
