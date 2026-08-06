# LP code review (frontend queue)

## Current evidence snapshot

- Active frontend task: `task-fe-03d-dom-state-tests` (`lp-runtime/progress.json`).
- Deterministic gate status: **gate-failure**, exit `2` (`lp-runtime/gate_summary.md`).
- Codex packet status: **`REVISE`** with explicit corrections and anti-pattern bans (`lp-runtime/codex-qwen3-extra-instructions.md`).
- First current defect: LP patch in `frontend/src/app/features/rag/rag-page.component.spec.ts` diverged from required bounded fixes and remains red.

## Diagnosis

This is a classic level-1 correction: one observable behavior bundle (DOM loading/reset/error assertions), one file, explicit method from Codex. No architecture or cross-layer expansion is needed.

## Bounded next action package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - `task-fe-03c-citations` accepted (already satisfied)
  - Active Codex REVISE packet must be followed exactly
- **allowed_paths:**
  - Canonical task scope: `frontend/**`, `docs/frontend/**` (task plan)
  - Effective bounded edit scope for this pass: `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate/constraint:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
  - Hierarchy closure still requires SURGICAL `ACCEPT` plus controller commit.
- **Required SURGICAL review:** Mandatory after LP gate-green evidence.

## Required content of the LP correction

Per the active Codex packet:

1. Remove defective synthetic additions (invalid shapes/selectors/manual loading mutations/innerHTML mutation patterns).
2. Implement the controlled-pending loading test with one service call assertion.
3. Implement two independent reset tests (success-reset and transport-error-reset) using rendered DOM assertions.
4. Preserve valid existing coverage and formatting discipline.

## Acceptance evidence required next cycle

1. Non-empty scoped diff in the target spec file.
2. Whitespace guard passes.
3. FE-03D gate exits `0` with consistent diagnostics.
4. Codex review returns `ACCEPT` before closure.

## Avoid repeating

- Do not invent current state values, fake selectors, invalid response payloads, or inconsistent diagnostics already called out in the Codex REVISE packet.
