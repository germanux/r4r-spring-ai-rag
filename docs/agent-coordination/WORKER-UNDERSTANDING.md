# Worker understanding validation

## PC understanding status

- Evidence indicates task understanding is directionally correct (task-07 objective unchanged), but operational closure is incomplete: request reason is `gate-green-checkpoint` with `checkpoint_head: null`.
- Interpretation: this is a **closure execution defect**, not a requirement ambiguity.

### PC next bounded instruction

- **Level / role / task:** Level 2 / PC / `task-07-populate-production-rag`
- **Dependencies:** none beyond already accepted prior backend tasks
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:**
  1. `git diff --check`
  2. canonical task-07 gate command from `.opencode/task-plan.backend.json`
- **Acceptance condition:** deterministic gate green and closure-ready evidence (no scope drift).

## LP understanding status

- Codex explicitly marked prior LP understanding as inadequate and tied defects to concrete anti-patterns in the modified spec.
- LP now has explicit corrected instructions and selector-level expectations; ambiguity is low.

### LP next bounded instruction

- **Level / role / task:** Level 1 / LP / `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations: ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Acceptance condition:** red-gate root causes removed; prescribed loading/disable/reset assertions pass with valid suite structure.

## Cross-worker risk controls

- Keep queues disjoint (backend vs frontend).
- Do not broaden task scope or bypass deterministic gates.
- Do not request SURGICAL; it is disabled and not required for PC/LP progression.
