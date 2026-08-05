# Agent memory

## Current state

- Last accepted task: None.
- Active task: task-01-base.
- Accepted: none.
- Remaining: task-01-base, task-02-ingestion, task-03-pgvector, task-04-rag.
- Exact plan: `.opencode/task-plan.json`.

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
