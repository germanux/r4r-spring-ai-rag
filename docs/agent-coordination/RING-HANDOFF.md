# Backend ↔ Frontend handoff

## Snapshot status

- **Backend (PC)**: `task-06e-child-process` gate is green but closure evidence is incomplete because current-run Codex decision is missing.
- **Frontend (LP)**: `task-fe-03b-answer-abstention` has Codex `REVISE`; required DOM-state test coverage is still pending implementation.

## Cross-stack coordination guidance

1. Backend should resolve review-state uncertainty first (Codex decision on existing gate-green evidence) before any new backend scope.
2. Frontend should execute the already-issued FE-03B correction packet now; this is correction work, not new feature expansion.
3. Keep ownership disjoint:
   - PC: backend/test packet scope only.
   - LP: `frontend/**` only, Angular major remains 17.

## Integration risks to watch

- If FE-03B abstention/error DOM semantics remain unverified, later citation/accessibility/final-validation tasks may stack on unstable UI behavior.
- If PC task-06e lacks Codex closure evidence, backend ingestion validation sequencing can be delayed even with green gate traces.

## Handoff acceptance checks

- PC handoff ready when `task-06e-child-process` has both: gate green and Codex `ACCEPT`.
- LP handoff ready when FE-03B correction packet is implemented, gate re-run is green, and Codex returns `ACCEPT`.
