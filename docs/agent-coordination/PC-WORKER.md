# PC code/runtime review (RUN 20260805T222913Z)

## Authoritative evidence reviewed

- `pc-runtime/progress.json` (active task still `task-06f-ingestion-validation`, status PENDING)
- `pc-runtime/memory.md` (latest exact gate recorded as `exit=2`, Codex decision `REVISE`)
- `pc-runtime/codex-qwen3-extra-instructions.md` (bounded fix already defined)
- `pc-git-status.txt` (newest snapshot includes unmerged paths)
- `pc-git-diff-stat.txt` (diff concentrated in test config + evidence artifacts)

## First current defect

The newest PC snapshot has unresolved merge conflicts:

- `UU .opencode/current/PC/manifest.json`
- `UU .opencode/current/PC/opencode/memory.backend.md`

This is the first blocking defect because it invalidates repository hygiene before the backend task gate can produce trustworthy evidence.

## Secondary defect (already identified by Codex REVISE)

After conflict cleanup, task-06f still needs the bounded REVISE fix:

1. Sanitize trailing whitespace in paths flagged by preflight.
2. In `src/test/resources/application.yml`, keep Flyway enabled, keep `JdbcMetricsAutoConfiguration` exclusion, and remove only `PgVectorStoreAutoConfiguration` from exclusions.

## Bounded next action for one worker pass

1. Resolve only the two unmerged PC evidence files to a coherent snapshot (no conflict markers, no unmerged index state).
2. Confirm whitespace preflight clean (`git diff --check`).
3. Apply the existing Codex REVISE config correction in `src/test/resources/application.yml` if still pending.
4. Run exact gate: `./scripts/task-gate.sh task-06f-ingestion-validation`.
5. Stop at the first new failure and preserve full diagnostics.

## Acceptance conditions

- No unmerged paths remain in PC worktree.
- `git diff --check` is clean before expensive gate run.
- `./scripts/task-gate.sh task-06f-ingestion-validation` exits `0`.
- Final task closure requires Codex decision `ACCEPT`.

## Do not repeat

- Do not rerun full backend gate while conflict/whitespace preflight defects are still present.
- Do not widen scope into Java test rewrites without new post-gate evidence.
