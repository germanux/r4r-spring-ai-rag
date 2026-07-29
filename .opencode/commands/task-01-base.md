# Task 01 — Base

## Objective

Keep the imported baseline green and reproducible.

## Required outcome

- Java 21 Spring Boot non-web application compiles and packages.
- Recursive Markdown loading and heading-aware bounded chunking remain deterministic.
- JDBC PostgreSQL driver, Flyway and Spring AI Ollama/PgVector dependencies remain.
- `postgres-app` is persistent and `postgres-test` is disposable.
- `PostgresBaselineIT` connects to PostgreSQL 16 with pgvector and Flyway.

## Restrictions

Do not add product functionality merely to make this task look larger. Do not add
REST, Angular, Playwright, Testcontainers or handwritten Ollama HTTP clients.

## Gate

`./scripts/task-gate.sh task-01-base`
