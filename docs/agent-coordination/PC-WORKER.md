# PC code review (backend)

## Snapshot reviewed

- `pc-runtime/progress.json`: active task is `task-06e-child-process` and status is still `PENDING`.
- `pc-runtime/codex-qwen3-extra-instructions.md`: latest Codex decision is `REVISE` with mandatory child-process test corrections.
- `pc-runtime/gate_summary.md`: gate summary is green, but no accompanying Codex ACCEPT artifact is present in this RUN_DIR.
- `pc-runtime/memory.md`: explicitly says no new acceptance claim is demonstrated in this run.

## First current defect

The backend queue has **unclosed acceptance state**: task `task-06e-child-process` remains pending while the latest authoritative Codex packet still contains unresolved `REVISE` instructions. A green gate summary alone does not satisfy task closure.

## Why this is the first defect

This is the earliest blocker to moving backend forward safely:

1. Task is pending in progress ledger.
2. Codex ACCEPT proof is missing.
3. Codex packet already defines an exact correction scope and rejects prior failed approaches.

Until this is reconciled, starting later tasks (`06f+`) would violate deterministic order and acceptance policy.

## Bounded next action for one PC pass

1. Execute only the Codex correction packet scope for `task-06e-child-process`.
2. Re-run exact gate: `./scripts/task-gate.sh task-06e-child-process`.
3. Hand refreshed evidence to Codex and stop.

## Acceptance conditions

- Gate exits `0` for `task-06e-child-process`.
- Codex returns `ACCEPT` for this task.
- Scope stays bounded to Codex packet targets; no production script/task-plan expansion.

## Avoid repeating

- Do not reintroduce `-Dcontext.initializer.classes` mechanism rejected by Codex.
- Do not use replacement objects not assignable to `KnowledgeIngestionService`.
- Do not treat a green gate summary as task acceptance without Codex ACCEPT evidence.

## Evidence paths

- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260805T163327Z/pc-runtime/progress.json`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260805T163327Z/pc-runtime/codex-qwen3-extra-instructions.md`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260805T163327Z/pc-runtime/gate_summary.md`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260805T163327Z/pc-runtime/memory.md`
