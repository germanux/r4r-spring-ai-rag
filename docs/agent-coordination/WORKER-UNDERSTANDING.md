# Worker understanding audit

## PC (backend) — required understanding

1. Active task is `task-06e-child-process`; it is not accepted yet.
2. A green gate snapshot does **not** close the task without Codex `ACCEPT`.
3. The latest Codex packet is authoritative and bounded; it specifically rejects:
   - unsupported initializer loading via `context.initializer.classes`,
   - non-type-compatible replacement for `KnowledgeIngestionService`.
4. One pass objective: apply packet-aligned correction, rerun exact gate, return evidence.

## LP (frontend) — required understanding

1. Active task is `task-fe-01-angular17-bootstrap`; gate is already green.
2. Missing artifact is Codex review outcome (current attempt failed transiently with exit 1 and zero steps).
3. One pass objective: recover Codex review first; no implementation churn unless Codex returns REVISE.
4. Task cannot advance to `task-fe-02-rag-client` until task-fe-01 has Codex ACCEPT.

## Shared operating constraints reaffirmed

- No worker writes Git history.
- No gate bypassing.
- No scope expansion beyond active task.
- Correction before new implementation.

## Evidence anchors

- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260805T163327Z/pc-runtime/codex-qwen3-extra-instructions.md`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260805T163327Z/pc-runtime/progress.json`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260805T163327Z/lp-runtime/codex_review.json`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260805T163327Z/lp-runtime/gate_summary.md`
