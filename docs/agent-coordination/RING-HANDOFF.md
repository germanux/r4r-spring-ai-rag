## Backend ↔ Frontend handoff (run 20260806T145914Z)

### Queue state summary
- **Backend (PC): HOLD**
  - Active task pointer is `task-07-populate-production-rag`, but dependency `BE-07-A:ACCEPTED` is not evidenced.
  - No backend product diff is present in this run snapshot.
- **Frontend (LP): CONTINUE**
  - Active task `task-fe-03c-citations` has a Codex `REVISE` packet and pending spec-only updates.

### Disjoint ownership decision
- Keep ownership disjoint this cycle:
  - PC performs **no backend write** until dependency unblocks.
  - LP performs **frontend spec-only correction** within FE-03C allowed path.

### Action packages

#### 1) Backend hold package
- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `BE-07-A:ACCEPTED`
- **allowed_paths:** none (hold)
- **Exact gate:** deferred until dependency satisfied (then task-07 exact gate in backend plan)
- **SURGICAL review requirement:** still mandatory for eventual closure

#### 2) Frontend revise package
- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03c-citations` / `FE-03C-A`
- **Dependencies:** `task-fe-03b-answer-abstention:ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `git diff --check` and `./scripts/frontend-task-gate.sh task-fe-03c-citations`
- **SURGICAL review requirement:** Codex `ACCEPT` required after gate evidence

### Integration risk to carry forward
If FE-03C revise scope leaks beyond the single spec file, controller scope checks may reject dispatch; keep the correction strictly LP-sized and evidence-complete.
