# Agent memory

## Current state

- No task has been accepted by the new automatic controller yet.
- The imported baseline contains Java 21, Spring Boot, Spring AI, JDBC, Flyway,
  PostgreSQL/pgvector, deterministic Markdown loading and chunking.
- PostgreSQL runs only in Docker: persistent application DB and disposable test DB.
- The exact ordered plan is `.opencode/task-plan.json`.

## Fixed decisions

- Non-web application until an explicit later task changes that scope.
- Flyway owns the application schema.
- Spring AI abstractions; no handwritten Ollama HTTP client.
- CodeGraph is available but not mandatory for every edit.
- Codex plans and reviews read-only; OpenCode edits; Python validates and commits.
- All execution evidence belongs under `runtime/runs/`.
- No automatic push.

## Last accepted task

None.
