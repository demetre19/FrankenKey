# DOMAIN-COMPLETE (2026-10-08) — lessons/frankenkey

Durable lesson — written by `crew learn`; indexed in `INDEX.md`. One bullet per lesson.

- Email-domain completion after @ = a worker-side CandidateSource pinning EMAIL_DOMAIN candidates from EditorContext.textBeforeCursor (EMAIL-class only, bounded scan); the typed-word model ends at @ and . so the accept path must re-derive the @… span from editor text and delete only the post-@ suffix — reusing commit_correction would append after the last label (bob@gmail.gmail.com).
