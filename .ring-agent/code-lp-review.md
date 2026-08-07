# LP code review (frontend)

## Current evidence reviewed

- `runtime/ring-agent/ring/20260805T202129Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260805T202129Z/worker-requests/LP.json`
- `runtime/ring-agent/ring/20260805T202129Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260805T202129Z/lp-runtime/memory.md`
- `runtime/ring-agent/ring/20260805T202129Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260805T202129Z/lp-runtime/checkpoint.json`
- `runtime/ring-agent/ring/20260805T202129Z/lp-runtime/codex-qwen3-extra-instructions.md`

## First current defect

The first current defect is **acceptance-evidence insufficiency for FE-03C despite a green gate**:

- Active task is `task-fe-03c-citations` and remains `PENDING`.
- A worker request exists with `reason: codex-revise` and `codex_decision: REVISE`.
- Checkpoint status is `no-product-diff` and request `changed_paths` is empty.
- Codex corrective instructions explicitly require missing rendered-DOM assertions for citation requirements.

## Bounded next action for one worker pass

Edit only:

- `frontend/src/app/features/rag/rag-page.component.spec.ts`

Add rendered-DOM tests that prove all FE-03C missing points from the correction packet:

1. Out-of-order citation input renders in expected ordered output with displayed ordinal, source, and full heading path segment order.
2. Response `{ answer: '...', abstained: false, citations: [] }` renders no `.citations-section`.
3. Citation-like text embedded in answer is not parsed into citation DOM when structured citations are empty.

Then run exactly:

- `./scripts/frontend-task-gate.sh task-fe-03c-citations`

## Acceptance conditions

- Exact gate exits `0` after new FE-03C assertions are present.
- Codex review for FE-03C returns `ACCEPT` on that state.
- Keep scope inside frontend task FE-03C; do not advance to FE-03D or broader UI changes in this pass.

## Avoid repeating

- Do not rely on an unchanged product diff plus green generic gate as FE-03C proof.
