# Task 02 — Deterministic ingestion

## Outcome

- Load all Markdown below the configured root in deterministic source order.
- Store source path, SHA-256 and ordered heading-aware chunks.
- Unchanged re-ingestion is exactly idempotent.
- Changed content atomically replaces only that source's chunks.
- PostgreSQL rollback preserves the previous checksum and ordered rows.
- Use Spring-managed loader/chunker/service and transaction-bound JDBC connections.

No Mockito, `@SpyBean`, AOP, reflection, manual service construction or production
failure hooks. The rollback test uses a temporary PostgreSQL `BEFORE INSERT` trigger.

Gate: `./scripts/task-gate.sh task-02-ingestion`
