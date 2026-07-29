# Current state

- Active slice: Benchmark 01 — baseline.
- Product scope: non-web Java 21 Spring Boot application.
- Infrastructure: Docker PostgreSQL/pgvector; app database persistent, test database disposable.
- Spring AI, JDBC, Flyway and CodeGraph are retained from the start.
- Excluded for now: REST, Angular, Playwright, Testcontainers, auto-commit and autopilot.
- Exact gate: `./scripts/verify.sh all`.
