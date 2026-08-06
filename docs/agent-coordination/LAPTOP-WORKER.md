# LP code review (Ring)

## Current queue status
- **Implementation level:** Level 1 (LP)
- **Assigned role:** LP
- **Active task:** `task-fe-03d-dom-state-tests`
- **Related work package:** `FE-03D-A`
- **Decision this cycle:** **CONTINUE**

## First current defect
The latest LP pass is still red (`gate exit 2`) on the owned spec file and carries an explicit Codex revise packet: missing required DOM assertions (rendered textarea/button disabled checks and reset-removal checks) plus whitespace/indentation cleanup.

## Evidence reviewed
- `runtime/ring-agent/ring/20260806T155109Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260806T155109Z/worker-requests/LP.json`
- `runtime/ring-agent/ring/20260806T155109Z/lp-runtime/codex_plan.json`
- `runtime/ring-agent/ring/20260806T155109Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260806T155109Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260806T155109Z/lp-runtime/memory.md`

## Bounded next action package
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Owner:** LP (Level 1)
- **Dependencies:** `task-fe-03c-citations:ACCEPTED` (already satisfied)
- **allowed_paths (canonical):** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** Mandatory before closure per review policy

### One-pass action
Revise only `rag-page.component.spec.ts` to:
1. remove trailing whitespace / malformed indentation,
2. assert rendered `textarea` and `.submit-button` are disabled during pending submit,
3. strengthen reset assertions to prove `.answer-content`, `.citations-section`, and `.error-state` removal with idle-state restoration,
4. run `git diff --check` before rerunning the exact gate.

## Acceptance conditions
1. Non-empty task-owned diff exists only in the allowed path.
2. `git diff --check` is clean.
3. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests` exits 0.
4. SURGICAL Codex review returns `ACCEPT`.

## Avoid repeating
- Another gate run with formatting defects.
- Generic “gate green” claims without explicit DOM-assertion mapping evidence.
