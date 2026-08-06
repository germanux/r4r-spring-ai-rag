# Worker understanding check (RUN 20260806T143139Z)

## PC (backend)

What PC must understand now:

1. `task-07-populate-production-rag` is active in progress metadata, but there is no current task-07 gate evidence in this run.
2. Hierarchy work package `BE-07-B` is dependency-gated by `BE-07-A:ACCEPTED`.
3. Therefore this pass is **HOLD**, not implement.

Required next behavior:

- Make no backend code edits this pass.
- Wait for explicit dependency release, then run one bounded task-07 pass and stop for SURGICAL review.

## LP (frontend)

What LP must understand now:

1. FE-03C exact gate is already green in current evidence.
2. Task remains PENDING because SURGICAL decision is not yet recorded.
3. Current dirty set includes non-task files that can block scope-clean closure.

Required next behavior:

- Do review-first flow: submit existing FE-03C evidence to SURGICAL.
- Avoid new edits unless Codex returns `REVISE`.
- If revised, keep edits strictly in `rag-page.component.spec.ts` and rerun exact gate.

## Shared non-negotiables

- No Git history operations by workers.
- Exact gate + SURGICAL ACCEPT are both required for closure.
- Do not broaden scope beyond declared allowed paths.
