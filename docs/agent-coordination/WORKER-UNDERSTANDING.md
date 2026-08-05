# Worker understanding snapshot

## PC (backend)

- **Current truth:** exact gate is green and task remains pending only because SURGICAL Codex acceptance is missing.
- **Do now (one pass):** maintain review posture; provide/retain current evidence for Codex review.
- **Do not do:** do not open new backend edits or rerun expensive gate loops without a new Codex `REVISE` instruction.
- **If REVISE arrives:** execute exactly one bounded correction inside `BE-06F-A` scope, rerun `./scripts/task-gate.sh task-06f-ingestion-validation`, and stop for review.
- **Completion bar:** exact gate green + Codex `ACCEPT`.

## LP (frontend)

- **Current truth:** task-fe-03c is still pending with Codex `REVISE`; there is in-flight spec work but no acceptance evidence.
- **Do now (one pass):** finish FE-03C rendered-DOM assertions in the single allowed spec file and run required preflight/gate.
- **Do not do:** do not claim completion from generic Angular success or from partial assertions that omit FE-03C required behaviors.
- **Required checks:** `git diff --check` clean, then `./scripts/frontend-task-gate.sh task-fe-03c-citations`.
- **Completion bar:** exact gate green + Codex `ACCEPT`.

## Shared constraints

- No Git history operations by workers.
- No scope widening beyond allowed paths.
- Every PC/LP closure requires SURGICAL Codex acceptance.
