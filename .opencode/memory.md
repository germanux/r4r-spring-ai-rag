# Agent memory

## Current state

- Last accepted task: task-03-pgvector.
- Active task: task-04-rag.
- Accepted: task-01-base, task-02-ingestion, task-03-pgvector.
- Remaining: task-04-rag.
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

- task-01-base: ACCEPTED — accepted at 2026-07-30T01:56:21.671118+00:00
- task-02-ingestion: ACCEPTED — accepted at 2026-07-30T14:21:45.327732+00:00
- task-03-pgvector: ACCEPTED — accepted at 2026-07-31T04:57:58.481883+00:00
- task-04-rag: PENDING — accepted at not accepted
