# LP code review (task-fe-03c-citations)

## Evidence reviewed

- `lp-runtime/progress.json` keeps `task-fe-03c-citations` in `PENDING`.
- `lp-runtime/codex-qwen3-extra-instructions.md` sets Codex decision to `REVISE` and gives mandatory FE-03C DOM assertions.
- `lp-runtime/memory.md` states the FE-03C behavior is still unproven and next action is to apply Codex extra instructions.
- `lp-git-status.txt` shows only `.opencode/memory.frontend.md` modified, with no task-owned product diff in this snapshot.

## Current diagnosis (first defect)

The first current defect is **proof gap in FE-03C tests**: task-specific rendered-DOM assertions required by Codex are missing from the current accepted evidence set, so a prior green gate is insufficient.

## Bounded next action

Edit only:

- `frontend/src/app/features/rag/rag-page.component.spec.ts`

Add the exact missing FE-03C DOM tests required by Codex:

1. Ordered rendering of structured citations even when response citations arrive out of order.
2. Absence of `.citations-section` for `{ abstained: false, citations: [] }` success responses.
3. No citation parsing from citation-like answer text when structured `citations` is empty.

Then run the exact gate for FE-03C.

## Acceptance conditions / gates

- `./scripts/frontend-task-gate.sh task-fe-03c-citations` exits `0`.
- Codex returns `ACCEPT` for FE-03C.
- Assertions validate rendered DOM behavior, not component internals/getters.

## Avoid repeating

- Do not rely on a generic green gate or unchanged product diff as FE-03C completion evidence.
