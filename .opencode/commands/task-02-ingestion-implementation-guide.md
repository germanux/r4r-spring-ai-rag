# Task 02 implementation guide

## Production contract

`KnowledgeIngestionService` receives `JdbcTemplate`, `MarkdownDocumentLoader` and
`HeadingMarkdownChunker` through constructor injection. `ingest()` is transactional.
For each document:

1. compute SHA-256 from UTF-8 content;
2. skip only when `(source_path, content_sha256)` already exists;
3. otherwise upsert the source and replace its chunks in deterministic ordinal order.

Catch only `EmptyResultDataAccessException` for an absent lookup. Other database
errors must propagate. Create PostgreSQL `TEXT[]` values inside
`JdbcTemplate.execute(ConnectionCallback)` using the transaction-bound connection;
free each SQL array. Keep the production SHA-256 helper package-private/static so the
unit test calls the real implementation.

## Required tests

- Same input twice preserves exact source/chunk state.
- Changed content replaces stale chunks and preserves source identity.
- Heading paths and ordinals are exact.
- Rollback test injects the Spring-managed service, seeds original content, captures
  checksum plus ordered rows, installs a temporary `BEFORE INSERT` trigger on
  `knowledge_chunks`, changes the file, calls `ingest()`, expects failure, drops the
  trigger in `finally`, and proves the original snapshot is unchanged.

## Restrictions

No mocks/failure hooks, no manual transactions, no direct extra connections, no test
that fails before reaching production code. Fix compilation one method at a time.

Gate: `./scripts/task-gate.sh task-02-ingestion`
