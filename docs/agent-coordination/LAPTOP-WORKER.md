# LP code review (Ring)

## Current evidence snapshot
- Active task: `task-fe-03c-citations` (`lp-runtime/progress.json`)
- Codex state in latest packet: `REVISE` with explicit missing FE-03C DOM assertions (`lp-runtime/codex-qwen3-extra-instructions.md`)
- LP worktree: modified `frontend/src/app/features/rag/rag-page.component.spec.ts`, modified memory file, and untracked `../../patches-applied/r4r-gemma4-lp.patch` (`lp-git-status.txt`)
- Diff magnitude: significant spec expansion in one file (`lp-git-diff-stat.txt`)

## First current defect
The first defect is **incomplete/unaccepted FE-03C correction evidence**. LP has pending spec changes but no demonstrated SURGICAL acceptance for the revised assertions.

## Bounded next action package
- **Work package:** `FE-03C-A`
- **Implementation level:** **Level 1**
- **Assigned role:** **LP**
- **Task ID:** `task-fe-03c-citations`
- **Dependencies:** `task-fe-03b-answer-abstention:ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03c-citations`
- **Required SURGICAL review:** mandatory `ACCEPT` before closure

### One-pass instruction
Complete FE-03C Codex-mandated rendered-DOM assertions in the spec file only:
1. Ordered structured citation rendering (out-of-order input, asserted output order, ordinal, source, heading-path segment order).
2. No citation section for `{ abstained:false, citations:[] }`.
3. Citation-like text in `answer` must remain answer text only; no `.citation-item`/`.citations-section` when structured citations are empty.
Then run `git diff --check` and the exact gate, and send the result for SURGICAL review.

## Acceptance conditions
1. Changes remain within `FE-03C-A` allowed path.
2. `git diff --check` passes.
3. `./scripts/frontend-task-gate.sh task-fe-03c-citations` exits `0`.
4. SURGICAL returns `ACCEPT` for FE-03C.

## Avoid repeating
- Treating generic Angular green runs as proof of FE-03C acceptance.
- Re-entering long sessions without narrowing to the first missing DOM assertion set.
- Allowing side artifacts (like `../../patches-applied/r4r-gemma4-lp.patch`) to distract from FE-03C-A scope.
