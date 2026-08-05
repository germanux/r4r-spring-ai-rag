# Backend ↔ Frontend handoff

## Coordination outcome

Both queues should continue their current active tasks with bounded correction-first passes. No cross-queue code ownership transfer is needed.

## Backend handoff (PC)

- Task: `task-06f-ingestion-validation`
- Required pass: hygiene + config correction, then exact backend gate rerun.
- Scope guard: only the prescribed sanitation and `src/test/resources/application.yml` exclusion adjustment; do not expand into unrelated backend refactors.

Acceptance gates:

- `git diff --check` clean.
- `./scripts/task-gate.sh task-06f-ingestion-validation` exit `0`.
- Codex `ACCEPT`.

## Frontend handoff (LP)

- Task: `task-fe-03c-citations`
- Required pass: implement Codex-requested rendered-DOM assertions in `rag-page.component.spec.ts` and rerun exact FE gate.
- Scope guard: frontend-only; no backend path edits.

Acceptance gates:

- `git diff --check` clean.
- `./scripts/frontend-task-gate.sh task-fe-03c-citations` exit `0`.
- Codex `ACCEPT`.

## Integration risks to watch next cycle

1. Backend preflight whitespace failures can invalidate expensive test runs before actual behavioral validation.
2. Frontend can produce superficially green runs while still missing FE-03C requirement-level proof.

## Evidence anchors

- `runtime/ring-agent/ring/20260805T212753Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260805T212753Z/pc-runtime/memory.md`
- `runtime/ring-agent/ring/20260805T212753Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260805T212753Z/lp-runtime/memory.md`
