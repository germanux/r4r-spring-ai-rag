# LP code review (frontend)

## Current authoritative evidence

- Active frontend task is `task-fe-03b-answer-abstention` and remains `PENDING`.
  - Evidence: `runtime/ring-agent/ring/20260805T170859Z/lp-runtime/progress.json`
- Latest Codex decision for this task is `REVISE` with a specific DOM-testing correction packet.
  - Evidence: `runtime/ring-agent/ring/20260805T170859Z/lp-runtime/codex-qwen3-extra-instructions.md`
- Checkpoint for attempt-01 is `no-product-diff` and `changed_paths` is empty.
  - Evidence: `runtime/ring-agent/ring/20260805T170859Z/lp-runtime/checkpoint.json`
  - Evidence: `runtime/ring-agent/ring/20260805T170859Z/worker-requests/LP.json`
- Generic deterministic gate is green (`exit 0`) but Codex explicitly says this is insufficient for FE-03B DOM acceptance.
  - Evidence: `runtime/ring-agent/ring/20260805T170859Z/lp-runtime/gate_summary.md`
  - Evidence: `runtime/ring-agent/ring/20260805T170859Z/lp-runtime/codex-qwen3-extra-instructions.md`

## First current defect

The first defect is **unimplemented Codex-mandated FE-03B DOM-state assertions** (and any resulting minimal template correction), evidenced by no product diff despite REVISE instructions.

## Bounded next action for one worker pass

Implement the correction packet in `frontend/src/app/features/rag/rag-page.component.spec.ts` using fixture-driven DOM assertions for all FE-03B states, apply only minimal `rag-page.component.html` edits if a test exposes a contract mismatch, then rerun `./scripts/frontend-task-gate.sh task-fe-03b-answer-abstention`.

## Acceptance conditions / gates

- `./scripts/frontend-task-gate.sh task-fe-03b-answer-abstention` returns exit `0`.
- Codex returns `ACCEPT` for `task-fe-03b-answer-abstention`.
- Tests explicitly validate: loading/disabled re-submit protection, non-abstained success DOM rendering, explicit nonblank abstention message, exact `Transport error occurred` error DOM text, and clear/reset restoration of idle UI + controls.

## Avoid repeating

- Do not submit another no-product-diff pass or mapping-free summary that relies only on a generic green gate.
