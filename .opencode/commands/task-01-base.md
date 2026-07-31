# Task 01 — Base

## Outcome

- Java 21 non-web Spring Boot application compiles/packages.
- Recursive Markdown loading and heading-aware bounded chunking remain deterministic.
- JDBC PostgreSQL, Flyway and Spring AI Ollama/PgVector dependencies remain.
- Persistent `postgres-app` and disposable `postgres-test` work.
- `PostgresBaselineIT` proves PostgreSQL 16, pgvector and Flyway.

Do not add product features, REST, frontend, Playwright, Testcontainers or handwritten
Ollama HTTP clients.

Gate: `./scripts/task-gate.sh task-01-base`
