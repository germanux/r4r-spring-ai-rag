# Backend ↔ Frontend Handoff

## Queue separation decision
- **Backend (PC queue): HOLD** on `task-07-populate-production-rag`.
- **Frontend (LP queue): START** revise on `task-fe-03d-dom-state-tests`.

This keeps backend/frontend ownership disjoint in this cycle and avoids overlapping write scopes.

## Backend handoff (PC)
- **Level / role:** Level 2, PC.
- **Task ID:** `task-07-populate-production-rag`.
- **Dependency status:** blocked for implementation by hierarchy dependency (`BE-07-B` requires `BE-07-A:ACCEPTED`).
- **Evidence:**
  - `runtime/ring-agent/ring/20260806T171721Z/pc-runtime/progress.json`
  - `runtime/ring-agent/ring/20260806T171721Z/pc-runtime/gate_summary.md`
  - `runtime/ring-agent/ring/20260806T171721Z/pc-git-status.txt`
- **Next bounded action:** no new PC coding pass; escalate current diff/gate package for SURGICAL review and dependency-sequencing confirmation.
- **Gate constraint when unblocked:** exact task-07 backend gate from `.opencode/task-plan.backend.json`.

## Frontend handoff (LP)
- **Level / role:** Level 1, LP.
- **Task ID:** `task-fe-03d-dom-state-tests`.
- **Dependency status:** unblocked.
- **Evidence:**
  - `runtime/ring-agent/ring/20260806T171721Z/worker-requests/LP.json`
  - `runtime/ring-agent/ring/20260806T171721Z/lp-runtime/codex-qwen3-extra-instructions.md`
  - `runtime/ring-agent/ring/20260806T171721Z/lp-runtime/gate_summary.md`
- **Next bounded action:** implement Codex REVISE assertions in the single scoped spec file, run whitespace check + exact frontend gate, then return for SURGICAL review.
- **Gate constraint:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`.

## Required SURGICAL review checkpoints
1. Review backend hold rationale and red-gate package before any renewed PC implementation.
2. Review LP revise patch after gate green; only `ACCEPT` can close LP task.
