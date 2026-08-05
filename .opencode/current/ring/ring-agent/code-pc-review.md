# PC backend review (RUN_ID 20260805T205823Z)

## Current evidence reviewed

- `pc-runtime/progress.json`: active task is `task-06f-ingestion-validation` (PENDING).
- `pc-runtime/gate_summary.md`: latest packaged gate for task-06f is `test-failure`, exit `1`.
- `pc-runtime/manifest.json`: gate summary source path maps to `task-06f-ingestion-validation/attempt-01`.
- `worker-requests/PC.json`: prior task `task-06e-child-process` is already `codex_decision=ACCEPT` and should not be reworked.

## First current defect

The first current backend defect is not in 06e; it is the active 06f gate failure. The packaged gate summary names failing/errored tests:

- `src/test/java/com/riansares/r4r/db/PostgresBaselineIT.java`
- `src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionCliTest.java`
- `src/test/java/com/riansares/r4r/rag/api/RagQueryControllerTest.java`

This is sufficient to prioritize correction on task-06f before any new backend scope.

## Bounded next action for one PC pass

1. Run exact gate: `./scripts/task-gate.sh task-06f-ingestion-validation`.
2. From `gate-full.log`, capture the **first current failing assertion/error**.
3. Apply one coherent backend fix scoped to that failure.
4. Re-run the same gate.

## Acceptance conditions

- Gate must pass: `./scripts/task-gate.sh task-06f-ingestion-validation` exit `0`.
- Task is complete only when Codex returns `ACCEPT` for task-06f.
- No unrelated task advancement during this repair pass.

## Avoid repeating

- Do not treat task-06e acceptance/checkpoint as proof for task-06f.
- Do not attempt broad multi-test rewrites before isolating the first failing assertion from current full gate evidence.
