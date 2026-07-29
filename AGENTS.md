# Repository agent rules

1. Work on exactly one active command from `.opencode/CURRENT_TASK.json`.
2. Keep the application non-web until a later explicit benchmark changes that scope.
3. PostgreSQL/pgvector runs only through `docker-postgres/compose.yml`.
4. Flyway is the sole owner of application schema changes.
5. Use Spring AI abstractions; do not create handwritten Ollama HTTP clients.
6. CodeGraph is available for impact analysis but is not a success gate.
7. Deterministic commands and current outputs own acceptance; prose does not.
8. Store logs, evidence, decisions and state only under `runtime/runs/<timestamp>/`.
9. Never commit, push, create worktrees, install system packages, or use `sudo` autonomously.
10. Do not add REST, Angular, Playwright, Testcontainers, retries or autopilot unless an explicit later benchmark requires them.
