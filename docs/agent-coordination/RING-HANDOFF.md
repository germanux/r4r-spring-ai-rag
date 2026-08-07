# Backend ↔ Frontend handoff

## Current queue status

- **Backend (PC):** actionable now; task-07 gate already green but closure evidence failed.
- **Frontend (LP):** temporarily blocked by `GLOBAL_ATTEMPT_LIMIT_REACHED` until reset/rearm.

## Ownership and scope separation

- **PC package** (`task-07-populate-production-rag`, Level 2): `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **LP package** (`task-fe-03d-dom-state-tests`, Level 1): `frontend/src/app/features/rag/rag-page.component.spec.ts`

These scopes are disjoint; no backend/frontend write overlap is introduced.

## Coordination decision

1. Continue PC immediately on closure-only pass.
2. Hold LP until attempt-budget reset is confirmed.
3. After reset, resume LP with the existing Codex REVISE packet and one exact FE-03D gate attempt.

## Integration risks to monitor

- Repeated backend gate-green runs without commit closure keep task-07 blocked indefinitely.
- Frontend queue can stall if attempt-budget reset is not explicitly rearmed.
- Any widening of LP scope beyond the single spec file risks repeating prior malformed rewrites.

## Evidence anchors

- `runtime/ring-agent/ring/20260807T024439Z/pc-runtime/controller_state.json`
- `runtime/ring-agent/ring/20260807T024439Z/pc-runtime/checkpoint.json`
- `runtime/ring-agent/ring/20260807T024439Z/lp-runtime/controller_state.json`
- `runtime/ring-agent/ring/20260807T024439Z/lp-runtime/codex-qwen3-extra-instructions.md`
