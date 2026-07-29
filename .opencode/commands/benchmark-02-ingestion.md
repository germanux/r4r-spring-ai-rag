# Benchmark 02 — Deterministic ingestion

Implement the smallest deterministic ingestion slice on top of the green baseline:
checksum, document identity, chunk identity, and idempotent re-ingestion. Use Flyway for
schema changes and integration tests against `postgres-test`. Do not add REST or chat.
Finish only after `./scripts/verify.sh all` is green.
