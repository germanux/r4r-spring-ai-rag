# LP code review — run 20260807T012027Z

## Current evidence reviewed

- `runtime/ring-agent/ring/20260807T012027Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T012027Z/lp-runtime/codex_plan.json`
- `runtime/ring-agent/ring/20260807T012027Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T012027Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T012027Z/lp-git-diff-stat.txt`

## Evidence-grounded diagnosis

The first current LP defect remains a **single-file test correction**:

- Deterministic gate is failing (`exit 2`).
- Codex plan marks this as local spec-file defects (structure/format + prohibited patterns), not infrastructure.
- Only one working file is currently modified: `frontend/src/app/features/rag/rag-page.component.spec.ts`.

## Bounded next action package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations: ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`

### One-pass action

Perform one bounded repair in the spec file only:

1. Restore valid suite structure and remove defective additions flagged by Codex instructions.
2. Implement only the prescribed tests:
   - controlled-pending loading/duplicate-submission DOM test,
   - independent success-reset test,
   - independent transport-error-reset test.
3. Preserve existing valid answer/abstention/citation/escaping/service-isolation coverage.
4. Run whitespace preflight, then exact FE-03D gate once.

### Exact gate

1. `git diff --check`
2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
3. Closure policy: `exact-gate-green + scope-clean + controller-commit`

## Avoid repeating

Do not reintroduce trailing whitespace, malformed braces, guessed selectors, `nativeElement.innerHTML` mutation, direct loading-flag mutation, or unnecessary `of`/`tick` patterns already rejected by Codex.

## Acceptance evidence expected from the worker

- Non-empty scoped patch in the single allowed file.
- `git diff --check` pass.
- FE-03D deterministic gate pass.
- Diagnostics and understanding report aligned to required DOM selectors/assertions.
