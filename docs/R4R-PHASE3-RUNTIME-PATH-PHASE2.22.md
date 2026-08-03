# R4R Phase 3 runtime-path stabilization — phase 2.22

This patch corrects three operational defects observed under cron/nohup:

1. `opencode`, `codex`, and the NVM `node` executable were visible in an interactive shell but absent from the supervisor environment.
2. heartbeat age formatting used Bash `printf %f`, which failed under locales using decimal commas.
3. obsolete and current branch-sync cron entries could run simultaneously.

## Runtime environment

`scripts/r4r-runtime-env.sh` reconstructs a deterministic user CLI path for cron and nohup, sources local R4R environment files, and resolves absolute paths for Node, npm, OpenCode, and Codex.

The helper is used by:

- `scripts/ensure-r4r-workers.sh`
- `scripts/run-ring-system.sh`
- `scripts/run-codex-agent.sh`

`setup.sh` records the resolved executable paths in `.env.r4r.local` without replacing operator-provided values.

## Locale-safe heartbeat reporting

Heartbeat age is formatted by Python rather than locale-sensitive Bash floating-point conversion.

## Cron normalization

Run:

```bash
./scripts/install-r4r-automation-cron.sh
```

It removes obsolete `R4R_AGENT_INTEGRATION`, duplicate `R4R_AGENT_BRANCH_SYNC`, and old guardian entries, then installs one canonical branch-sync job.

Google Drive import/autocommit entries are intentionally preserved.
