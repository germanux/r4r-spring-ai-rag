# Benchmark 01 — Baseline

Keep the minimal baseline green. It must compile with Java 21 and retain:

- deterministic recursive Markdown discovery and heading-aware bounded chunking;
- Spring AI Ollama and PgVector dependencies/configuration without custom HTTP clients;
- JDBC PostgreSQL driver and Flyway;
- persistent app PostgreSQL plus disposable test PostgreSQL from `docker-postgres/`;
- unit tests and `PostgresBaselineIT`.

Do not add REST, Angular, Playwright, autonomous commits, or a second orchestration layer.
Make only changes required by real failing evidence. Final gate: `./scripts/verify.sh all`.
