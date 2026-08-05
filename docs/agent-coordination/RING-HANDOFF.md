# Backend ↔ Frontend Handoff

## Coordination status

- **Backend (PC)**: `task-06e-child-process` is active and blocked on completing a Codex `REVISE` packet with fresh exact-gate + Codex evidence.
- **Frontend (LP)**: `task-fe-03b-answer-abstention` is active and blocked on implementing Codex-mandated DOM-state assertions with fresh gate + Codex evidence.

Both queues are independent in ownership and can proceed concurrently.

## What backend needs from frontend

No immediate backend dependency on LP output for current pass. Continue bounded FE-03B test/template corrections only.

## What frontend needs from backend

No immediate frontend dependency on PC output for current pass. Continue bounded child-process IT/test infrastructure corrections only.

## Cross-stack risks to monitor

1. **Evidence freshness risk**: both manifests show null for run artifacts (`codex_review`, `gate_summary`, `checkpoint`), so no closure proof exists yet.
2. **Scope-drift risk**: recent shared-history imports touched frontend RAG files; LP must keep FE-03B changes narrowly tied to DOM-state acceptance requirements.
3. **False-green risk**: prior snapshots include gate-green metadata while tasks remain pending; neither queue should treat gate green alone as acceptance without Codex `ACCEPT`.

## Bounded next synchronization point

After each worker completes one pass, require these artifacts before changing queue state:

- exact gate log for active task,
- Codex review decision (`ACCEPT`/`REVISE`),
- checkpoint/changed-path evidence showing scope compliance.

## Acceptance constraints restated

- PC: `./scripts/task-gate.sh task-06e-child-process` must exit 0 and then receive Codex `ACCEPT`.
- LP: `./scripts/frontend-task-gate.sh task-fe-03b-answer-abstention` must exit 0 and then receive Codex `ACCEPT`.
