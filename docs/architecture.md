# Architecture

## Product flow

Markdown -> deterministic loader/chunker -> idempotent ingestion -> Spring AI
PgVector -> retrieval -> cited non-web RAG service.

## Agent flow

Task plan -> bounded local plan -> OpenCode implementation -> deterministic gate ->
controller progress/memory update -> task-owned commit -> next task. Surgical review
is currently disabled and is not a closure gate.

The controller is deliberately small and bounded. It does not use worktrees,
background supervisors, autonomous push, REST acceptance, browser automation or a
second task planner.

## Ownership

- `src/`, `knowledge/`: product code and corpus.
- `.opencode/`: versioned agents, commands and task plans; progress, memory and
  `.opencode/current/` are ignored machine-local state.
- `py-codex-agent/`: automatic controller, prompts, schemas and tests.
- `docker-postgres/`: PostgreSQL Compose, init, persistent data and backups.
- `scripts/`: public operational entry points and deterministic gates.
- `runtime/`: ignored logs, diagnostics, recovery hashes and control state.
- `.ring-agent/evidence/`: durable, single-writer task evidence created only on a
  semantic transition.
- `docs/`: human documentation.
