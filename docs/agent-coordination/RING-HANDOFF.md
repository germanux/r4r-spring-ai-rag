# Backend ↔ Frontend handoff — RUN 20260805T234824Z

## Queue separation check

- **Backend/PC active task:** `task-06f-ingestion-validation` (level 2, PC).
- **Frontend/LP active task:** `task-fe-03c-citations` (level 1, LP).
- Current write scopes are disjoint:
  - PC: `src/test/resources/application.yml`, `.opencode/current/PC/**` (only if REVISE path is triggered)
  - LP: `frontend/src/app/features/rag/rag-page.component.spec.ts`
- No cross-queue path overlap is required in this cycle.

## Backend handoff decision

- **Status:** Ready for SURGICAL review routing, not for new implementation expansion.
- **Why:** Gate is already green with no product diff, but SURGICAL `ACCEPT` evidence is missing.
- **Bounded next action:** reviewer pass first; corrective coding only on explicit `REVISE`.

## Frontend handoff decision

- **Status:** Continue implementation on current LP package.
- **Why:** Codex `REVISE` explicitly requires additional rendered-DOM FE-03C assertions.
- **Bounded next action:** complete FE-03C-A in one file, run preflight + exact gate, return to SURGICAL review.

## Integration risk notes

1. Advancing backend plan stages before Codex `ACCEPT` on `task-06f-ingestion-validation` would violate mandatory review policy.
2. Advancing frontend to `task-fe-03d-dom-state-tests` before FE-03C acceptance risks carrying an unproven citation contract forward.
3. LP memory/gate metadata appears partially stale relative to current gate summary; enforce evidence-pack completeness before closure claims.

## Required acceptance conditions (both queues)

- Exact task gate green for the active task.
- Scope-clean changes within each task `allowed_paths`.
- SURGICAL Codex review result `ACCEPT`.
- Controller-owned closure actions only (no worker Git-history operations).
