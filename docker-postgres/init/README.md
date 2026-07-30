# Container initialization

This directory is mounted at `/docker-entrypoint-initdb.d` for both PostgreSQL services.
Keep it only for container-level bootstrap that must run on a fresh data directory.

Do not create application tables here. Flyway owns the application schema through
`src/main/resources/db/migration/`, so the application and test databases receive the
same versioned schema.
