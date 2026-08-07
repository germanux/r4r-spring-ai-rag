# Architecture

## Product flow

Markdown -> deterministic loader/chunker -> idempotent ingestion -> Spring AI
PgVector -> retrieval -> cited non-web RAG service.

## Agent flow

Canonical task plan -> Ring assignment -> OpenCode worker implementation ->
deterministic gate -> global acceptance ledger -> next dependency-ready task. Ring
uses GPT-5.6 Luna, both full-stack workers use GPT-5.6 Terra, and only Ring may
request an on-demand GPT-5.6 Sol escalation.

The controller is deliberately bounded. Assignments are expiring, scoped and
single-use; worker sessions have time, activity, step, repetition and context
limits. A final canonical gate must be green after every task is globally accepted.

## Ownership

- `src/`, `knowledge/`: product code and corpus.
- `.opencode/`: versioned agents, commands and task plans; progress, memory and
  `.opencode/current/` are ignored machine-local state.
- `py-ring-agent/`: Ring coordinator, shared OpenCode worker, contracts and tests.
- `docker-postgres/`: PostgreSQL Compose, init, persistent data and backups.
- `scripts/`: public operational entry points and deterministic gates.
- `runtime/`: ignored logs, diagnostics, recovery hashes and control state.
- `.ring-agent/evidence/`: durable, single-writer task evidence created only on a
  semantic transition.
- `docs/`: human documentation.
- `docs/archive/`: non-runtime historical controller and superseded profiles.
