# Architecture

## Product flow

Markdown -> deterministic loader/chunker -> idempotent ingestion -> Spring AI
PgVector -> retrieval -> cited non-web RAG service.

## Agent flow

Task plan -> Codex read-only plan -> OpenCode implementation -> deterministic gate
-> Codex read-only review -> controller progress/memory update -> local commit -> next
task.

The controller is deliberately small and bounded. It does not use worktrees,
background supervisors, autonomous push, REST acceptance, browser automation or a
second task planner.

## Ownership

- `src/`, `knowledge/`: product code and corpus.
- `.opencode/`: OpenCode agent, commands, ordered task plan and concise progress.
- `py-codex-agent/`: automatic controller, prompts, schemas and tests.
- `docker-postgres/`: PostgreSQL Compose, init, persistent data and backups.
- `scripts/`: public operational entry points and deterministic gates.
- `runtime/`: ignored logs, decisions, evidence and resumable task lock.
- `docs/`: human documentation.
