# LP code review (frontend queue)

## Evidence reviewed

- `runtime/ring-agent/ring/20260806T000832Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260806T000832Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260806T000832Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260806T000832Z/lp-git-diff-stat.txt`
- `runtime/ring-agent/ring/20260806T000832Z/lp-runtime/gate_summary.md`

## First current defect

LP is still in `task-fe-03c-citations` revise flow with an unaccepted spec diff.

- Current dirty product file: `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- Codex extra instructions explicitly require missing rendered-DOM assertions (ordered structured citations, empty-citation omission, and non-parsing of citation-like answer text).
- No ACCEPT evidence is present in this RUN_DIR snapshot.

The first defect is therefore **incomplete FE-03C proof in tests**, not a cross-layer architecture issue.

## Bounded next action package

- **Implementation level:** 1
- **Assigned role:** LP
- **Task ID:** `FE-03C-A` under parent `task-fe-03c-citations`
- **Dependencies:** `task-fe-03b-answer-abstention:ACCEPTED` (satisfied)
- **allowed_paths:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03c-citations`
- **Required SURGICAL review:** mandatory after gate and scope-clean evidence

### One-pass instruction

In one LP pass, finish FE-03C-A exactly as Codex mandated:

1. Add DOM assertions for out-of-order structured citations rendered in correct order with ordinal, source, and complete heading path segment order.
2. Add DOM assertion that `.citations-section` is absent for `{ abstained:false, citations:[] }` success responses.
3. Add DOM assertion that citation-like text in answer body is not parsed into citation items when structured citations are empty.
4. Run `git diff --check` and the exact frontend gate.
5. Hand off to SURGICAL for ACCEPT/REVISE.

## Avoid repeating

- Do **not** stop at generic green runs with incomplete FE-03C assertions.
- Do **not** edit component/template unless newly failing focused tests prove a real component defect.
