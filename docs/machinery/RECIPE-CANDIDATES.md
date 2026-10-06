# Recipe Candidates — postmortem-proposed auto-remediations

Keyed by defect class; `crew postmortem` refreshes each row in
place. `coverage: existing-recipe` names the live recipe that
already remediates the class — extend it rather than rebuilding.
`coverage: needs-recipe` is a recipe-shaped proposal: a reviewer
promotes it into `src/crew/recipes.py` (fn + registry row).

| Class | Recipe | Coverage | Sightings |
|---|---|---|---|
