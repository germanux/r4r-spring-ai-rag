# LP code review (frontend queue)

## Current evidence snapshot

- Active LP task is `task-fe-03d-dom-state-tests`.
- Worker request manifest includes `codex-revise` for this task.
- Latest gate summary is red (`exit=2`, gate-failure).
- Codex packet explicitly lists remaining defects in `rag-page.component.spec.ts` (missing DOM assertions plus whitespace/indentation fixes).

Evidence:

- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T160956Z/worker-request-manifest.json`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T160956Z/worker-requests/LP.json`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T160956Z/lp-runtime/gate_summary.md`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T160956Z/lp-runtime/codex_plan.json`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T160956Z/lp-runtime/codex-qwen3-extra-instructions.md`

## First current defect

The owned frontend spec still does not satisfy the complete Codex checklist before gate re-run. Specifically, the packet calls out missing rendered DOM assertions and formatting defects that caused/preceded gate failure.

## Bounded next action package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED` (already satisfied in LP progress)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests` (after `git diff --check`)
- **Required SURGICAL review:** mandatory `ACCEPT` before closure

### Pass objective

Single-file revise pass only:

1. Remove whitespace/indentation defects.
2. Add/complete the missing DOM-state assertions listed in Codex instructions.
3. Keep existing answer/abstention/transport-alert coverage intact.
4. Run preflight + exact gate and provide requirement-to-assertion mapping.

### Acceptance evidence required

1. `git diff --check` passes with no whitespace errors.
2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests` exits 0.
3. SURGICAL Codex review returns `ACCEPT` for this pass.

## Avoid repeating

Do **not** perform another gate attempt without first implementing every explicitly listed Codex DOM assertion and formatting correction.
