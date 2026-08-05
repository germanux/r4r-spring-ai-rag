# R4R full-stack task queues

## Decision

- PC and LP alternate together between backend and frontend.
- Initial phase duration: 12 hours; the dispatcher must make it configurable.
- Each machine has an independent queue in each phase.
- The four new plans are additive and do not replace the two active legacy plans until the dispatcher and progress migration are implemented.

## New queues

| Phase | PC | LP |
|---|---|---|
| Backend | `.opencode/task-plan.backend-pc.json` | `.opencode/task-plan.backend-lp.json` |
| Frontend | `.opencode/task-plan.frontend-pc.json` | `.opencode/task-plan.frontend-lp.json` |

## Pending product assignments

### backend-pc

1. `task-06e-child-process`
2. `task-07-populate-production-rag`
3. `task-09-production-smoke`

These tasks stay on PC because they exercise the child JVM, persistent PC pgvector database, Docker and real HTTP production smoke.

### backend-lp

1. `task-06f-ingestion-validation` — starts only after `task-06e-child-process` is integrated.
2. `task-08-rag-semantic-evaluation` — starts only after the production corpus from task 07 is available or the test provides deterministic fixtures.

### frontend-lp

1. `task-fe-03-rag-ui`
2. `task-fe-03b-answer-abstention`
3. `task-fe-03c-citations`

LP owns the feature implementation sequence and keeps its currently active RAG page work.

### frontend-pc

1. `task-fe-03d-dom-state-tests`
2. `task-fe-03e-security-accessibility`
3. `task-fe-03f-final-validation`
4. `task-fe-04-playwright`

PC owns verification after LP's baseline is integrated. It must not modify the same files concurrently with LP; Ring must hold this queue until `task-fe-03c-citations` is accepted and synchronized.

## Canonical tasks not placed in the new pending queues

Backend tasks `task-01-base` through `task-06d-failure-classification` are recorded as accepted in the latest backend memory and must remain historical.

Frontend tasks `task-fe-01-angular17-bootstrap` and `task-fe-02-rag-client` precede the currently reported `task-fe-03-rag-ui`. Their acceptance must be verified from the live progress file before activation; if either is not accepted, prepend it to `frontend-lp` rather than silently skipping it.

## Auxiliary command files reviewed

The following files are supporting evidence, not independent queue entries:

- `task-02-ingestion-implementation-guide.md`
- `task-03-pgvector-current-defects.md`
- `task-03-pgvector-focused-recovery-guide.md`
- `task-03-pgvector-implementation-guide.md`
- `task-03-pgvector-incremental-compile-recovery.md`

`task-web-gallery.md` is legacy and remains outside the RAG full-stack cycle.

## Operational backlog

`docs/R4R_BACKLOG_IMPLEMENTACION_SIGUIENTE_CHAT_2026-08-05.md` contains Ring/harness/model/synchronization tasks. They are not product work and must not be inserted into PC/LP backend or frontend queues without current repository evidence.

## Activation requirements

1. Add phase-aware selection to `config/r4r-agents.json` and `scripts/resolve-r4r-config.mjs`.
2. Persist separate progress and memory files for all four queues.
3. Migrate accepted task states by task ID; never initialize accepted work as pending.
4. Use one shared phase anchor so PC and LP switch together after 12 hours.
5. Stop between tasks at the phase deadline; do not kill a task mid-gate.
6. Ring must enforce the two cross-queue dependencies documented above.
7. Keep the existing active plans until the migration passes deterministic tests.
