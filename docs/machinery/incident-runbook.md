# Incident Runbook — the machinery's defect-class ledger

Living surface: `crew postmortem` refreshes the class index and
the per-class `**Sightings:**` lines on every pass; the recorder
(`src/crew/incidents.py`) dedups sightings at write time. New
classes arrive as stub sections — fill in recovery + prevention,
then register the signature in `incidents.FAILURE_CLASSES`.

Managed sections: `## Class index` and each `**Sightings:**`
line are regenerated — edit the prose, never those rows.

## Class index

| Class | Category | Remediation | Recipe | Sightings |
|---|---|---|---|---|
| `scope-pin-stale` | scope | recipe | scope-restore | 0 |
| `merge-footprint-underscope` | scope | needs-human | — | 0 |
| `bootstrap-break` | conflict | needs-human | — | 0 |
| `staged-prompt-no-enter` | dispatch | recipe | staged-prompt-verify | 0 |
| `scope-dir-trailing-slash` | scope | recipe | scope-dir-expand | 0 |
| `sleep-guard-false-positive` | fence | needs-human | — | 0 |
| `checkout-theirs-clobber` | conflict | needs-human | — | 0 |
| `python3-read-as-write` | fence | needs-human | — | 0 |
| `shared-registry-collision` | conflict | needs-human | — | 0 |
| `serial-merge-race` | conflict | needs-human | — | 0 |
| `watchdog-stale-probe` | watchdog | needs-human | — | 0 |
| `done-not-pushed` | done | recipe | done-surface-reap | 0 |
| `provider-dead-stream` | provider | needs-human | — | 0 |
| `unclassified` | ? | ? | — | 13 |

## scope-pin-stale

**Sightings:** 0

- **Signature:** .crew-scope changed but crew-scope.sha256 pin stale → silent commit-hook deny
- **Recovery:** shasum -a 256 .crew-scope > <gitdir>/crew-scope.sha256 (the gitdir pin file lives at .git/worktrees/<wt>/crew-scope.sha256 for a worktree)
- **Prevention:** any .crew-scope widen outside backend.py:2300 must re-pin the sha256 file in the same operation

## merge-footprint-underscope

**Sightings:** 0

- **Signature:** dispatch --paths covers feature files but git diff main...branch + shared zones escape the fence
- **Recovery:** merge-dispatch derives write_paths from the branch diff plus the shared-zone set — never a hand widened scope
- **Prevention:** merge gate resolves the declared scope from the branch diff before the merge runs; underscoped dispatches are refused upstream

## bootstrap-break

**Sightings:** 0

- **Signature:** a merge conflict lands in cli.py → cli.py <verb> cannot run → no verb to dispatch the fix
- **Recovery:** manual `herdr agent start` into the MAIN checkout — the fix is dispatched outside the broken verb surface
- **Prevention:** cli.py parse-failure on main is release-blocking; a parse check is a merge gate, not a post-facto discovery

## staged-prompt-no-enter

**Sightings:** 0

- **Signature:** prompt text staged into the pane but the submit keystroke never landed; agent_status stays idle
- **Recovery:** verify agent_status=working after every prompt; re-send ENTER when it reports idle
- **Prevention:** the dispatch/post-prompt hook asserts agent_status=working before reporting the spawn done

## scope-dir-trailing-slash

**Sightings:** 0

- **Signature:** `tests` (no slash) does not cover `tests/x.py` — a dir write_path only matches itself
- **Recovery:** widen the entry to `tests/` — the trailing slash is the dir marker the scope checker requires
- **Prevention:** scope expansion appends the slash to every dir token at dispatch time; the fence never sees a bare dir
- **Lesson:** SCOPE-DIR-TRAILING-SLASH-2026-09-26

## sleep-guard-false-positive

**Sightings:** 0

- **Signature:** the guard matches any `sleep N` token — including bounded retry-until-condition loops — and is blind to boss-wait being down
- **Recovery:** match the death-loop pattern, not the token; allow bounded condition-exit loops; fall back when boss-wait errors
- **Prevention:** guards classify the loop shape (unbounded poll) before denying, never the command text alone

## checkout-theirs-clobber

**Sightings:** 0

- **Signature:** `git checkout --theirs <file>` resolves the conflict but silently drops ours' non-conflicted edits
- **Recovery:** per-block resolution only — never whole-file --theirs/--ours on a file with edits on both sides
- **Prevention:** the conflict-resolution contract requires hunk-level evidence (both sides kept) before a resolved file may be committed

## python3-read-as-write

**Sightings:** 0

- **Signature:** `python3 -c open(<scoped>)` reads a file the fence classifies as a mutation → denied read
- **Recovery:** use shasum / cat / git show on scoped files — never python; the fence only blesses read-only verbs
- **Prevention:** the scoped-file contract names the read verb set; python on fenced paths is a fence-circumvention signature

## shared-registry-collision

**Sightings:** 0

- **Signature:** autonomy.js / INDEX.md / test-tail appends — every branch inserts at the same anchor, every merge conflicts
- **Recovery:** generated-not-committed INDEX.md; per-feature test files instead of one shared tail
- **Prevention:** shared append-targets are regenerated artifacts or sharded per feature — the conflict class disappears at the schema level

## serial-merge-race

**Sightings:** 0

- **Signature:** two merge-and-close runs interleave → the second overwrites the first's tip; commits lost silently
- **Recovery:** one main merge at a time — the merge lock serializes callers; a lost-tip retry replays the dropped range
- **Prevention:** merge-and-close is mutex-guarded end-to-end (lock → merge → push → verify tip), never just around the merge command

## watchdog-stale-probe

**Sightings:** 0

- **Signature:** tab_rename / worktree probes evaluate state the run already left — stale sha inputs, wrong verdicts
- **Recovery:** re-probe at current head before acting; a stale input aborts the action, never fires on it
- **Prevention:** every probe binds the sha it ran against and verifies it is still HEAD before the verdict lands

## done-not-pushed

**Sightings:** 0

- **Signature:** agent reports done while the worktree is dirty or the branch tip never reached the remote
- **Recovery:** verify head≠base AND pushed before treating done as landed — the verify-done gate, never the status word
- **Prevention:** done is a state gate (dirty/pushed/stamped), never a turn-end signal; premature-done nudges once per verdict fingerprint

## provider-dead-stream

**Sightings:** 0

- **Signature:** stopReason:error as the terminal session row (errorId 135168 socket kill, oh-my-pi#12498) with the pane still reporting working and the session file flat past grace — the turn is dead while the surface looks live
- **Recovery:** the night sweep writes a pane-inbox record with deliver:nextTurn — the hook-injection path (pi.sendUserMessage) starts the resume turn; after the per-session nudge cap escalate to the operator (provider blackhole, breaker fix lane owns in-session recovery)
- **Prevention:** upstream oh-my-pi#12498 (server idle socket kill); local mitigations are effort tier + the spend-guard breaker steer lane — detection here is the floor

## unclassified

**Sightings:** 13

_unclassified — needs a signature row in `incidents.FAILURE_CLASSES` plus recovery and prevention text._
