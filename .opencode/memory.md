# Agent memory

## Current state

- Last accepted task: task-01-base.
- Active task: None.
- Accepted: task-01-base.
- Remaining: task-02-ingestion, task-03-pgvector, task-04-rag.
- Exact plan: `.opencode/task-plan.json`.

## Fixed decisions

- Non-web application until an explicit later task changes scope.
- PostgreSQL only in Docker; Flyway owns application schema.
- Spring AI abstractions; no handwritten Ollama HTTP client.
- Codex plans/reviews read-only; OpenCode edits; Python validates and commits.
- CodeGraph is optional impact analysis, not a success gate.
- Runtime evidence stays under `runtime/runs/`; no automatic push.

## Task commits

- task-01-base: ACCEPTED — accepted at 2026-07-29T17:43:37.875467+00:00
- task-02-ingestion: PENDING — accepted at not accepted
- task-03-pgvector: PENDING — accepted at not accepted
- task-04-rag: PENDING — accepted at not accepted
