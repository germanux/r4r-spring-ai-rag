# Worker understanding audit

## PC understanding check
Evidence indicates PC correctly preserved a gate-green `task-06f` attempt with no product diff, but closure criteria remain unmet because SURGICAL acceptance is missing.

### Required understanding for next pass
- A green exact gate is **not** completion.
- `task-06f` closes only after SURGICAL `ACCEPT`.
- Do not open new backend implementation scope until current review decision is recorded.

### Concrete package restatement
- **Level 2 / PC / BE-06F-A**
- **Task ID:** `task-06f-ingestion-validation`
- **Dependencies:** `task-06e-child-process:ACCEPTED`
- **allowed_paths:** `src/test/resources/application.yml`, `.opencode/current/PC/**`
- **Gate:** `./scripts/task-gate.sh task-06f-ingestion-validation`
- **Reviewer:** SURGICAL (mandatory)

## LP understanding check
Codex packet shows LP previously mis-scoped FE-03C understanding toward generic green runs. Current evidence still shows pending FE-03C with an unaccepted spec diff.

### Required understanding for next pass
- FE-03C is assertion-completeness work, not generic build stability work.
- Rendered DOM assertions must prove structured citation behavior and non-parsing of citation-like answer text.
- Completion requires exact gate green **and** SURGICAL `ACCEPT`.

### Concrete package restatement
- **Level 1 / LP / FE-03C-A**
- **Task ID:** `task-fe-03c-citations`
- **Dependencies:** `task-fe-03b-answer-abstention:ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Gate:** `./scripts/frontend-task-gate.sh task-fe-03c-citations`
- **Reviewer:** SURGICAL (mandatory)

## Ring cycle note
No repository code edits were performed by Ring in this cycle; only staged coordination artifacts were produced.
