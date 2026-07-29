# Architecture

```text
src/                    Java product and Flyway migrations
knowledge/              Markdown corpus
.opencode/              OpenCode agent, commands, task and concise memory
py-codex-agent/          One bounded Python controller and strict review contract
docker-postgres/         PostgreSQL/pgvector compose, bind data and backups
scripts/                 Public shell entry points
runtime/                 Generated runs, logs, evidence and decisions
docs/                    Human documentation
.codegraph/              Regenerable local index, ignored by Git
```

The product path is deliberately incremental:

1. deterministic Markdown loading/chunking and real PostgreSQL baseline;
2. idempotent ingestion;
3. Spring AI PgVectorStore and embeddings;
4. cited non-web RAG;
5. bounded OpenCode/Codex cycle.

PostgreSQL application data uses `docker-postgres/data/app/`. Integration tests use
a second service backed by `tmpfs`; Testcontainers is intentionally absent.
