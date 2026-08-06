# Backend ↔ Frontend handoff

## Queue separation and ownership

- **Backend (PC track)**
  - Active task: `task-07-populate-production-rag`
  - Current status: gate-green checkpoint requested, awaiting mandatory SURGICAL review
  - Current changed backend paths (from request):
    - `docs/backend/production-ingestion-evidence.md`
    - `src/main/java/com/riansares/r4r/ingestion/KnowledgeIngestionService.java`
    - `src/main/java/com/riansares/r4r/vector/PgVectorKnowledgeStore.java`
    - `src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionServiceIT.java`
    - `src/test/java/com/riansares/r4r/ingestion/TestChildApplicationContextInitializer.java`

- **Frontend (LP track)**
  - Active task: `task-fe-03d-dom-state-tests`
  - Current status: deterministic gate red; Codex `REVISE` pending implementation
  - Bounded edit scope for next pass:
    - `frontend/src/app/features/rag/rag-page.component.spec.ts`

These scopes are disjoint (backend Java/docs vs frontend spec), so LP correction can continue while PC remains review-held.

## Coordinated next actions

1. **PC lane (hold for review):**
   - **Level 3 / SURGICAL review-only package** on current `task-07` checkpoint.
   - No additional PC implementation until Codex returns `ACCEPT` or `REVISE`.

2. **LP lane (continue bounded correction):**
   - **Level 1 / LP package** to apply exact Codex corrections in one spec file.
   - Run `git diff --check` then exact FE-03D gate once.

## Cross-stack dependency notes

- Frontend task `task-fe-03d-dom-state-tests` is DOM-state test stabilization and does not require backend task-07 acceptance to proceed.
- Backend `task-07` acceptance remains prerequisite for later backend tasks (`task-08`, `task-09`) but does not block immediate LP corrective testing scope.

## Required acceptance policy for both lanes

- `exact-gate-green + scope-clean + surgical-accept + controller-commit` per `.opencode/task-plan.hierarchy.json`.
