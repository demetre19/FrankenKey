# AutoBuild Agent: Programmatic-First Rule

## Binding rule

Never use a user interface when an API, command, direct service or data operation, source-level harness, or automated test can achieve the same result.

Use UI only when:

1. the UI itself is the behavior being tested or verified; or
2. no programmatic surface exists after checking the available APIs, commands, services, files, databases, test hooks, and automation interfaces.

When UI is unavoidable, automate only the smallest UI segment. Keep setup, fixture creation, data extraction, comparison, scoring, retries, and verification programmatic.

## Required decision order

1. Use an existing API, SDK, connector, or service endpoint.
2. Use a deterministic command or direct data/file operation.
3. Use a source-level test, instrumentation hook, or purpose-built automation harness.
4. Use UI automation only for the remaining UI-specific behavior.
5. Use manual UI interaction only when automation is technically unavailable, and state the exact blocker.

## AutoBuild application

- Inspect supplied APKs, packages, binaries, manifests, resources, models, and runtime data programmatically before editing the port.
- Capture large behavior corpora through direct harnesses or tests; do not replay every case through UI when the same contract can be exercised programmatically.
- Reserve device/emulator UI checks for compact end-to-end smoke tests of behavior that crosses the actual UI boundary.
- Prefer machine-readable fixtures and assertions over screenshots or visual transcription.
- Do not replace observable UI verification with source inspection alone when appearance, interaction, focus, accessibility, or rendering is the requested contract.

## Completion check

Before using UI, the agent must be able to state either:

- `UI behavior is the contract under test`; or
- `No equivalent programmatic surface exists after checking: <specific surfaces checked>`.

If neither statement is true, the agent must use the programmatic path.
