# Backend ↔ Frontend handoff

## Current queue posture

- **PC/backend**: still on `task-06e-child-process`; acceptance is not closed because Codex ACCEPT evidence is missing and a REVISE packet is still active.
- **LP/frontend**: `task-fe-01-angular17-bootstrap` gate is green, but Codex review invocation failed transiently, so acceptance is still open.

## Cross-stack dependency status

No new cross-stack API coupling is required in this cycle. Both queues should complete their current acceptance closure first:

1. PC closes backend task-06e acceptance.
2. LP closes frontend task-fe-01 acceptance.

Only after those closures should new feature handoffs be started (backend task 06f+/frontend task-fe-02+).

## Bounded directives

### For PC
- Stay within backend task scope (`task-06e-child-process`) and Codex packet file boundaries.
- Deliver: exact gate result + Codex ACCEPT/REVISE outcome.

### For LP
- Prioritize review-path recovery (Codex rerun) over code churn.
- Deliver: Codex decision for current green checkpoint; edit only if REVISE.

## Acceptance gates to preserve

- Backend: `./scripts/task-gate.sh task-06e-child-process` must be green and Codex must return `ACCEPT`.
- Frontend: `./scripts/frontend-task-gate.sh task-fe-01-angular17-bootstrap` must be green and Codex must return `ACCEPT`.

## Risks to track

- If PC uses a previously rejected initializer strategy again, backend will loop on REVISE.
- If LP retries implementation instead of fixing review execution, frontend will loop without decision artifacts.
