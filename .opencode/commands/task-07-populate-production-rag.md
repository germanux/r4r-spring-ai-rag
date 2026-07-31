# Task 07 — Populate the production editorial RAG on the PC

## Ownership

This is a PC/backend operational task. It uses the production application PostgreSQL
container and the local PC Ollama embedding model. Do not edit `frontend/**`. Do not
use the laptop model for embeddings or database population.

## Objective

Run the Task 06 ingestion CLI against the configured canonical corpus and persistent
application database, prove that pgvector contains managed chunks, and prove a second
identical run is idempotent.

This task must populate the actual application database configured by `.env`; it must
not claim success from the disposable integration-test database.

## Safety boundary

Before any mutation:

1. verify that `.env` resolves the intended application container, database and corpus;
2. verify the container is running and PostgreSQL responds;
3. verify `nomic-embed-text` is published by the PC Ollama endpoint;
4. count existing `vector_store` rows and distinct managed sources;
5. preserve all existing rows until the ingestion service applies its normal
   source-scoped replacement contract.

Never run:

- `DROP`, `TRUNCATE`, database recreation or volume deletion;
- an unscoped `DELETE FROM vector_store`;
- destructive graph indexing/wipe tools;
- manual insertion of fake vectors;
- a handwritten embedding HTTP request.

Do not print or commit passwords from `.env`.

## Canonical corpus

Use the configured `RAG_KNOWLEDGE_PATH`. Discovery must remain recursive and
deterministic according to the accepted ingestion contract.

Do not silently index:

- `README.md`;
- generated logs;
- JSON/CSV sidecars unless the accepted loader explicitly supports them;
- documents explicitly marked as excluded by the existing metadata contract;
- files outside the configured canonical root.

Do not rewrite the corpus merely to make ingestion easier.

## Required execution

Run the exact Task 06 CLI with the environment loaded from the repository `.env`.

Capture evidence for two consecutive successful executions:

### First execution

Record:

- timestamp;
- resolved corpus root;
- embedding model and dimension;
- database/container identity without credentials;
- vector rows and managed source count before execution;
- the exact `R4R_INGESTION_RESULT` JSON;
- vector rows and managed source count after execution.

The result must contain at least one persisted vector and one managed source.

### Second identical execution

Run the same command without changing corpus or configuration. Prove:

- the command succeeds;
- total managed vector count does not grow due to duplicates;
- source identities remain stable;
- unchanged sources are skipped or replaced without duplicate accumulation according
  to the accepted ingestion contract;
- no unmanaged row is deleted.

Do not infer idempotency solely from a success exit code.

## Evidence document

Create or replace:

`docs/backend/production-ingestion-evidence.md`

It must contain only reproducible, non-secret evidence:

- execution timestamps;
- Git commit under test;
- corpus root;
- Ollama endpoint host and model name, but no tokens or credentials;
- PostgreSQL container/database names, but no password;
- exact CLI command with secrets represented by environment variable names;
- first and second JSON summaries;
- SQL count queries and numeric results;
- explicit idempotency conclusion;
- remaining limitations.

Do not paste full source documents or embeddings.

## Focused verification

Add focused backend tests only when a production defect is discovered. Any repair must
stay in the existing discovery/ingestion/vector-store contracts and include a
regression test.

The production evidence must distinguish:

- discovered Markdown files;
- accepted managed sources;
- persisted vector rows;
- unchanged sources;
- deleted managed sources when applicable.

## Acceptance matrix

| ID | Requirement | Required evidence |
|---|---|---|
| A1 | Correct PC dependencies | Ollama model and PostgreSQL checks |
| A2 | Correct production corpus | Resolved canonical root and deterministic source count |
| A3 | Database populated | `vector_store` row count greater than zero |
| A4 | Source metadata present | Distinct non-blank `metadata->>'source'` values |
| A5 | Second run idempotent | Before/after counts and CLI summaries |
| A6 | No destructive mutation | Scoped SQL/service inspection and unchanged foreign rows |
| A7 | Evidence is reproducible | Complete sanitized evidence document |
| A8 | Regression-free | Clean full backend gate |

A green Maven build alone cannot satisfy this task.

## Exact gate

Run exactly the controller-supplied gate. It performs:

1. a clean full backend gate;
2. the production ingestion CLI using `.env`;
3. a SQL assertion that `vector_store` contains at least one row.

Do not pipe or rewrite that command.

## Completion

Completion requires the production database to be populated, the second run to be
idempotent, the sanitized evidence document to be committed, Codex `ACCEPT`, and no
push.
