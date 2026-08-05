# Agent memory

## Current state

- Last accepted task: None.
- Active task: task-01-base.
- Accepted: none.
- Remaining: task-01-base, task-02-ingestion, task-03-pgvector, task-04-rag, task-05-rag-api, task-06-production-ingestion-cli, task-07-populate-production-rag, task-08-rag-semantic-evaluation, task-09-production-smoke.
- Exact plan: `{self.plan_display}`.

## Fixed decisions

- Non-web application until an explicit later task changes scope.
- PostgreSQL only in Docker; Flyway owns application schema.
- Spring AI abstractions; no handwritten Ollama HTTP client.
- Codex plans/reviews read-only; OpenCode edits; Python validates and commits.
- Every red gate produces a full diagnostic log and compressed source bundle for Codex.
- Identical Codex planning evidence is rate-limited; changed failures bypass the cooldown.
- CodeGraph is focused and advisory by default; unavailable MCP evidence does not stop repair.
- Runtime evidence stays under `runtime/runs/`; no automatic push.

## Task commits

- task-01-base: PENDING — accepted at not accepted
- task-02-ingestion: PENDING — accepted at not accepted
- task-03-pgvector: PENDING — accepted at not accepted
- task-04-rag: PENDING — accepted at not accepted
- task-05-rag-api: PENDING — accepted at not accepted
- task-06-production-ingestion-cli: PENDING — accepted at not accepted
- task-07-populate-production-rag: PENDING — accepted at not accepted
- task-08-rag-semantic-evaluation: PENDING — accepted at not accepted
- task-09-production-smoke: PENDING — accepted at not accepted
