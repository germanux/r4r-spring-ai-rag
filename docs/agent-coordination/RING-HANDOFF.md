# Backend ↔ Frontend handoff

## Ownership and concurrency guard

- **PC (backend)** remains on `task-06e-child-process`.
- **LP (frontend)** remains on `task-fe-03c-citations`.
- Queues are disjoint this cycle; no cross-owned file edits requested.

## What backend needs from frontend

Nothing blocking for this pass. LP should complete FE-03C test-evidence closure independently.

## What frontend needs from backend

Nothing immediate for FE-03C. However, backend Task 06E is still gate-failing, so later backend-dependent validation phases remain risked until PC closes 06E.

## Integration risks to track

1. **Backend readiness risk**: Task 06E gate is still red (exit 2), so backend progression to 06f/07+ is not yet reliable.
2. **Frontend scope risk**: FE-03C pass should be assertion-focused; component behavior changes in this pass could introduce unnecessary regressions.

## Bounded cross-stack next checkpoint

- Wait for:
  - PC: gate-green + Codex ACCEPT on `task-06e-child-process`.
  - LP: gate-green + Codex ACCEPT on `task-fe-03c-citations` with the new DOM assertions.
- After both are evidenced, reassess downstream dependency coupling.
