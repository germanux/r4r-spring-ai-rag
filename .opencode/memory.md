# Agent memory

## Current state

- Last accepted task: task-02-ingestion.
- Active task: None.
- Accepted: task-01-base, task-02-ingestion.
- Remaining: task-03-pgvector, task-04-rag.
- Exact plan: `.opencode/task-plan.json`.

## Fixed decisions

- Non-web application until an explicit later task changes scope.
- PostgreSQL only in Docker; Flyway owns application schema.
- Spring AI abstractions; no handwritten Ollama HTTP client.
- Codex plans/reviews read-only; OpenCode edits; Python validates and commits.
- CodeGraph is optional impact analysis, not a success gate.
- Runtime evidence stays under `runtime/runs/`; no automatic push.

## Task commits

- task-01-base: ACCEPTED — accepted at 2026-07-30T01:56:21.671118+00:00
- task-02-ingestion: ACCEPTED — accepted at 2026-07-30T14:21:45.327732+00:00
- task-03-pgvector: PENDING — accepted at not accepted
- task-04-rag: PENDING — accepted at not accepted
