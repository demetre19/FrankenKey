# FK-S1-REQUIREMENTS (2026-10-06) — lessons/machinery

Durable lesson — written by `crew learn`; indexed in `INDEX.md`. One bullet per lesson.

- FK S1 build blockers: local.properties sdk.dir pointed at unmounted /Volumes volume in 4 checkouts → repoint to ~/Library/Android/sdk; compileComposeSequences needs bare `python` on PATH (only python3 exists — symlink required); S1.plan.json base_sha must name a sha in the TARGET repo (autocorrect checkout), not the plan repo; backend_template path landed by boss on fix/crew-json-backend-template c3d3931
