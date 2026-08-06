# LP code review (Ring)

## Current evidence snapshot

- Active frontend task: `task-fe-03d-dom-state-tests` (`lp-runtime/progress.json`).
- Latest deterministic gate is green, exit `0` (`lp-runtime/gate_summary.md`).
- Checkpoint result: `no-product-diff` with `product_paths: []` (`lp-runtime/checkpoint.json`).
- Worker request for attempt-03 also reports `changed_paths: []` and `codex_decision: null` (`worker-requests/LP.json`).
- Prior Codex packet remains `REVISE` with explicit missing assertions in
  `lp-runtime/codex-qwen3-extra-instructions.md`.

## First current defect

LP reran a green gate without producing scoped product changes after a mandatory REVISE packet that required concrete test assertions.

This is a process-and-evidence defect: the acceptance gap remains open because required corrections were not evidenced in the latest attempt.

## Directed action package

- **Implementation level:** Level 1 (LP)
- **Assigned role:** LP frontend worker
- **Task ID:** `task-fe-03d-dom-state-tests` (work package focus: `FE-03D-A`)
- **Dependencies:**
  - `task-fe-03c-citations:ACCEPTED` (already satisfied)
  - Apply all unresolved Codex REVISE assertions from the latest packet
- **allowed_paths:**
  - Canonical FE-03D-A scope: `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** mandatory `ACCEPT` before closure

## Bounded next LP pass

In one pass, implement only the mandated assertion set in the spec file:

1. Loading-state DOM assertions for `.loading-state[role="status"]`, `textarea`, and `.submit-button` disabled behavior.
2. Single duplicate-submit guard assertion (exactly one additional submit while pending; total service calls remain one).
3. Split reset coverage into independent success-reset and transport-error-reset tests.
4. Produce explicit requirement-to-assertion mapping in local understanding evidence.

## Acceptance conditions

1. Scoped diff present in the exact spec file (no production code changes).
2. `git diff --check` clean.
3. Exact frontend gate green.
4. SURGICAL Codex review returns `ACCEPT`.

## Avoid repeating

Do not submit another gate-green/no-diff attempt; implement every explicit REVISE item first.
