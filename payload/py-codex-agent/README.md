# R4R Codex/OpenCode controller

The controller runs one locked task at a time:

1. exact task gate with disposable PostgreSQL when required;
2. deterministic diagnostic classification;
3. untruncated gate log plus compressed implicated-file bundle;
4. focused advisory CodeGraph map;
5. read-only local pre-edit understanding;
6. read-only Codex plan;
7. bounded OpenCode edit;
8. exact task gate, local assimilation and Codex review;
9. controlled progress/commit on green plus `ACCEPT`.

Identical diagnostic fingerprints reuse the latest Codex plan during
`R4R_CODEX_MIN_INTERVAL_SECONDS` (default 3600). New evidence bypasses the cooldown.
CodeGraph defaults to `R4R_CODEGRAPH_POLICY=advisory` so MCP outages do not mask Maven
or source evidence.

Run from the repository root:

```bash
./scripts/run-codex-agent.sh
./scripts/run-codex-agent.sh --status
```

For a manual Maven lifecycle that needs the disposable integration database:

```bash
./scripts/mvn-with-test-db.sh install
```

## Resume model

Active-task lock files are disabled. The controller resumes from
`.opencode/progress.json` and accepts task-scoped dirty work plus maintenance paths.
A stale `runtime/locks/active-task.json` is deleted on startup.
