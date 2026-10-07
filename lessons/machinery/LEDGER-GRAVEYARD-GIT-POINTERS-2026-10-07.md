# LEDGER-GRAVEYARD-GIT-POINTERS (2026-10-07) — lessons/machinery

Durable lesson — written by `crew learn`; indexed in `INDEX.md`. One bullet per lesson.

- Committed scratch .git worktree-pointer files (absolute gitdir: refs into deleted runs/ trees) inside the ledger graveyard fatally break "git status" for the whole ledger clone → host-doctor ledger-clone red → every queued row starves. Surgical fix: delete the stale pointer files; prevention row queued: fix-ledger-graveyard-git-pointers.
