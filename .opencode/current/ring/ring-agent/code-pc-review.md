# PC code review (backend)

## Current task and status

- Active task: `task-06f-ingestion-validation` (`PENDING`).
- Latest request: `codex-revise` with `gate_exit: 2`.
- No new acceptance has been demonstrated for 06f.

Evidence:

- `runtime/ring-agent/ring/20260805T212753Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260805T212753Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260805T212753Z/pc-runtime/memory.md`

## First current defect

The pass did not complete the deterministic validation loop because the latest run is in REVISE with preflight/gate failure (`exit 2`). The correction packet explicitly requires two bounded fixes before rerunning the exact gate:

1. sanitize trailing whitespace in controller-published Markdown artifacts; and
2. in `src/test/resources/application.yml`, remove **only** `PgVectorStoreAutoConfiguration` from exclusions while preserving Flyway enablement and the JDBC metrics exclusion.

This means the first defect is not new feature behavior; it is a blocked validation cycle caused by hygiene/config mismatch against codified constraints.

Evidence:

- `runtime/ring-agent/ring/20260805T212753Z/worker-requests/PC.json` (authoritative next action)
- `runtime/ring-agent/ring/20260805T212753Z/pc-git-diff-stat.txt` (only product path changed: `src/test/resources/application.yml`)
- `runtime/ring-agent/ring/20260805T212753Z/pc-runtime/memory.md` (explicit avoid-repeat guidance)

## Bounded next action for one worker pass

1. Apply the REVISE packet exactly (no Java test rewrites, no scope expansion).
2. Run `git diff --check` and clear any whitespace errors.
3. Run exact gate: `./scripts/task-gate.sh task-06f-ingestion-validation` from clean `target/`.
4. If red, capture first failing assertion and keep full diagnostics for Codex.

## Acceptance conditions

- `git diff --check` clean.
- `./scripts/task-gate.sh task-06f-ingestion-validation` exits `0`.
- Codex decision for task 06f is `ACCEPT`.

## Avoid repeating

- Do not treat exit `2` as proof of backend logic failure and jump into broad test refactors.
- Do not bypass sanitation/preflight and spend another full gate cycle on preventable whitespace failures.
