# PostgreSQL/pgvector

- `postgres-app`: persistent development database on port `55432`; files live in `data/app/`.
- `postgres-test`: disposable integration-test database on port `55433`; data lives in `tmpfs`.
- `init/`: container bootstrap only.
- Application schema: Flyway migrations packaged under `src/main/resources/db/migration/`.
- Portable backups belong in `backups/` and should be produced with `pg_dump`.

All variable values come from the repository-root `.env` through `scripts/db.sh`.
