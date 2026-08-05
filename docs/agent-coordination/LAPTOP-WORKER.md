# LP code review (frontend)

## Current evidence reviewed

- `runtime/ring-agent/ring/20260805T164348Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260805T164348Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260805T164348Z/lp-runtime/checkpoint.json`
- `runtime/ring-agent/ring/20260805T164348Z/lp-runtime/local_understanding.md`
- `runtime/ring-agent/ring/20260805T164348Z/worker-requests/LP.json`

## First current defect

`task-fe-01-angular17-bootstrap` remains **PENDING** with Codex **REVISE**.

Codex identifies a concrete unresolved defect: production build selection is not proven to use `environment.prod.ts`. The latest checkpoint is `no-product-diff`, confirming no corrective product edit landed in the last pass.

## Bounded next action for one worker pass

Apply one focused frontend correction:

- Update `frontend/angular.json` so production build resolves `src/environments/environment.prod.ts` (preserve development replacement and Angular 17 constraints).

Then rerun exactly:

- `./scripts/frontend-task-gate.sh task-fe-01-angular17-bootstrap`

And include a requirement-to-file mapping in the next local understanding report.

## Acceptance conditions (must all hold)

1. Exact gate exits 0: `./scripts/frontend-task-gate.sh task-fe-01-angular17-bootstrap`.
2. Codex review returns `ACCEPT` for `task-fe-01-angular17-bootstrap`.
3. Scope remains inside `frontend/**` (no unrelated RAG/UI feature work).
4. Angular major stays 17.

## Avoid repeating

- Another unchanged run with `changed_paths: []` / `no-product-diff`.
- A local-understanding report that omits requirement-to-file mapping.
