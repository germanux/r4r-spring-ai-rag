# Backend ↔ Frontend handoff (RUN_ID 20260805T191433Z)

## Current shared status

- Backend (PC) active: `task-06e-child-process` (pending).
- Frontend (LP) active: `task-fe-03b-answer-abstention` (pending).
- Both workers show local edits but no fresh gate/codex artifacts were captured in this RUN_DIR snapshot.

## What backend needs to preserve for frontend stability

1. Keep existing RAG API response contract stable while working on task-06e (this task is process-validation oriented, not API redesign).
2. Avoid introducing side effects that force frontend tests to depend on a live external LLM.
3. If backend discovers error-shape changes during task-06e, publish them explicitly before LP advances past FE-03B.

## What frontend needs to preserve for backend compatibility

1. Keep answer/abstention rendering tied to typed API fields rather than model-text parsing.
2. Keep deterministic transport-error handling compatible with backend failure classification semantics already accepted in prior backend tasks.
3. Ensure FE-03B tests validate DOM states only; no coupling to backend runtime availability.

## Immediate bounded coordination actions

- **PC**: run `./scripts/task-gate.sh task-06e-child-process`, then attach first failure/green evidence tied to child JVM process behavior.
- **LP**: run `./scripts/frontend-task-gate.sh task-fe-03b-answer-abstention`, then attach first failure/green evidence tied to answer/abstention/error/reset DOM behavior.

## Acceptance checkpoints before cross-stack advancement

- PC task-06e gate green + Codex `ACCEPT`.
- LP FE-03B gate green + Codex `ACCEPT`.
- No unannounced API contract drift introduced while FE-03B is in progress.
