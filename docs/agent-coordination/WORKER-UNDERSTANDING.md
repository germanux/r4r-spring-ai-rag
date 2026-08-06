# Worker understanding alignment — RUN 20260806T003326Z

## PC (backend) — required understanding for next pass

1. Current state is **gate-green, no-product-diff, not closed**.
2. The missing artifact is a **SURGICAL decision**, not another gate rerun.
3. `task-06f-ingestion-validation` closure still requires:
   - exact gate evidence (`./scripts/task-gate.sh task-06f-ingestion-validation`, exit `0`), and
   - SURGICAL Codex `ACCEPT` per `.opencode/task-plan.hierarchy.json`.

### PC bounded next action

- **Implementation level:** Level 3
- **Assigned role:** SURGICAL reviewer
- **Task ID:** `task-06f-ingestion-validation` (`BE-06F-A` context)
- **Dependencies:** existing run `20260806T001814Z` evidence package
- **allowed_paths:** read-only review
- **Exact gate:** validate existing gate-green evidence; emit `ACCEPT` or `REVISE`
- **Required SURGICAL review:** yes (this pass)

## LP (frontend) — required understanding for next pass

1. FE-03C is still **REVISE**, not complete.
2. Existing spec edits are unaccepted until preflight + exact gate + SURGICAL review complete.
3. Mandatory proof is rendered DOM behavior for citation contract, including:
   - ordered structured citation rendering,
   - no citation section for empty citations,
   - no parsing of citation-like text from answer body.

### LP bounded next action

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03c-citations` (`FE-03C-A`)
- **Dependencies:** codex revise packet; `task-fe-03b-answer-abstention:ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03c-citations`
- **Required SURGICAL review:** yes, mandatory for closure

## Shared anti-drift reminders

- Do not widen scope beyond task `allowed_paths`.
- Do not claim completion without direct gate and SURGICAL evidence.
- Do not bypass deterministic preflight or exact gate definitions.
