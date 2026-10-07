# FIX-GRAMMAR-DEAD-ENUM (2026-10-07) — lessons/android

Durable lesson — written by `crew learn`; indexed in `INDEX.md`. One bullet per lesson.

- Exec.commandLine stringifies a Provider to provider(?) instead of resolving it — providers only auto-resolve in executable=/args setters. b8c6172 wrapped pythonExe in providers.provider{} for config-cache and broke every python Exec task, truncating ComposeKeyData.java to 0 bytes. Fix: resolve the provider in doFirst (executable = pythonExe.get()) so the probe stays at execution time. Also: a lane brief can name a base (main) while the defect lives on another lineage (sprint/autobuild) — verify the defect site exists on the worktree base before editing, and ask early.
