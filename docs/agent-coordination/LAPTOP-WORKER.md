# LP code/runtime review (RUN 20260805T222913Z)

## Authoritative evidence reviewed

- `lp-runtime/progress.json` (active task `task-fe-03c-citations`, status PENDING)
- `lp-runtime/memory.md` (Codex decision pending; repeated idle-timeout history)
- `lp-runtime/codex-qwen3-extra-instructions.md` (explicit REVISE packet with missing DOM assertions)
- `lp-runtime/gate_summary.md` (generic green snapshot)
- `lp-git-status.txt` / `lp-git-diff-stat.txt` (no task-owned product diff captured in this ring snapshot)

## First current defect

FE-03C acceptance proof is incomplete. The latest gate summary is green, but Codex REVISE requires missing rendered-DOM assertions in `rag-page.component.spec.ts`, and task remains PENDING.

## Required bounded correction

Edit only:

- `frontend/src/app/features/rag/rag-page.component.spec.ts`

Add all three missing FE-03C proofs in rendered DOM:

1. Out-of-order citations render in ordered sequence with ordinal, source, and full heading path.
2. `{ abstained: false, citations: [] }` does not render `.citations-section`.
3. Citation-like text in answer body is not parsed into `.citation-item` / `.citations-section` when structured citations are empty.

## Bounded next action for one worker pass

Implement the three spec assertions above, run `git diff --check`, then run exact gate:

- `./scripts/frontend-task-gate.sh task-fe-03c-citations`

If red, stop at first new failure and preserve diagnostics.

## Acceptance conditions

- FE gate exits `0`.
- DOM-level FE-03C assertions are present and passing.
- Task closure only after Codex decision `ACCEPT`.

## Do not repeat

- Do not treat a generic green run as sufficient FE-03C proof.
- Do not allow another idle-timeout loop without implementing the REVISE packet.
