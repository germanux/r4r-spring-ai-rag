# LP code review (frontend)

## Current task and status

- Active task: `task-fe-03c-citations` (`PENDING`).
- Latest local sessions show repeated `idle-timeout` with no task-owned product diff.
- Codex packet remains `REVISE` and asks for missing rendered-DOM coverage.

Evidence:

- `runtime/ring-agent/ring/20260805T212753Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260805T212753Z/lp-runtime/memory.md`
- `runtime/ring-agent/ring/20260805T212753Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260805T212753Z/lp-git-diff-stat.txt`

## First current defect

The task proof is incomplete: FE-03C requires specific rendered-DOM citation assertions, but current evidence shows no implementation pass that delivered those assertions. A generic green gate summary is insufficient because Codex explicitly required additional task-specific tests.

Required missing proof (from Codex extra instructions):

1. ordered structured citations render in order with ordinal/source/heading path checks;
2. `.citations-section` omitted when `citations: []` in non-abstained success response;
3. citation-like text inside answer body is not parsed into structured citation DOM.

## Bounded next action for one worker pass

1. Modify only `frontend/src/app/features/rag/rag-page.component.spec.ts`.
2. Add the three required rendered-DOM tests exactly as specified.
3. Run `git diff --check`.
4. Run exact gate: `./scripts/frontend-task-gate.sh task-fe-03c-citations`.

## Acceptance conditions

- `git diff --check` clean.
- `./scripts/frontend-task-gate.sh task-fe-03c-citations` exits `0`.
- Codex decision is `ACCEPT` for FE-03C.
- Evidence explicitly demonstrates the three DOM behaviors above.

## Avoid repeating

- Do not run another idle-timeout pass without editing the target spec file.
- Do not stop at generic gate-green output without FE-03C requirement-level assertions.
