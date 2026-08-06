# LP Code Review (Ring)

## Current evidence reviewed
- `runtime/ring-agent/ring/20260806T171721Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260806T171721Z/worker-requests/LP.json`
- `runtime/ring-agent/ring/20260806T171721Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260806T171721Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260806T171721Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260806T171721Z/lp-git-status.txt`

## First current defect
LP has a gate-green checkpoint, but SURGICAL Codex explicitly returned `REVISE` and requested specific missing DOM assertions. Current snapshot shows no new product-file edit after that request (only memory is modified in `lp-git-status.txt`). The defect is unexecuted revise instructions.

## Decision
- **Implementation level:** Level 1 (LP).
- **Assigned role:** LP (frontend).
- **Task ID:** `task-fe-03d-dom-state-tests`.
- **Action:** `START` one bounded revise pass.

## Bounded next action package
- **Owner:** LP.
- **Dependencies:**
  - `task-fe-03c-citations:ACCEPTED` (already satisfied in progress evidence).
- **allowed_paths:**
  - Primary constrained path from Codex packet: `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Must implement exactly from Codex packet:**
  - Loading DOM assertions (`.loading-state[role="status"]`, disabled textarea/button).
  - Double-submit guard assertion while request is pending.
  - Split reset behavior into success-reset and transport-error-reset tests with explicit before/after DOM assertions.
  - Provide requirement-to-assertion mapping in evidence.
- **Required review:** SURGICAL Codex review after gate green; close only on `ACCEPT`.

## Acceptance conditions for this coordination cycle
1. One scoped file change only.
2. Deterministic frontend gate exits 0 after the revise patch.
3. Codex review returns `ACCEPT` before task closure.

## Avoid repeating
- Do not re-run unchanged gate-green output without implementing all explicit REVISE deltas.
