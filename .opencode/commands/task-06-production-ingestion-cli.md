# Task 06 — Explicit production knowledge-ingestion CLI

## Ownership

This task belongs exclusively to the PC/backend worker and the backend task plan.
Do not edit `frontend/**`, Angular files, Playwright files, Git history, controller
runtime code, progress files or worker memory.

## Objective

Add one explicit, testable, non-web command that ingests the configured canonical
knowledge directory into the existing PostgreSQL/pgvector store.

The normal web application must not ingest automatically merely because it starts.
Production ingestion must happen only when the dedicated CLI is invoked.

## Required reading and reconnaissance

Read only:

1. `AGENTS.md`;
2. `.opencode/commands/task.md`;
3. this task;
4. the current PC memory state and current Codex correction, when present;
5. the exact existing ingestion, catalog, chunking, vector-store and configuration
   classes needed to reuse their public contracts.

Use no more than five reconnaissance calls before the first focused validation or
source edit. Do not traverse `frontend/**`, `node_modules/**`, `target/**`,
`runtime/**`, `.git/**`, `.r4r/**`, `.codegraph/**` or PostgreSQL data directories.

Run the exact task gate before assuming what is missing.

## Required implementation

Create a dedicated Java entry point named:

`com.riansares.r4r.ingestion.KnowledgeIngestionCli`

The entry point must:

1. create a Spring application context using the existing application configuration;
2. force `WebApplicationType.NONE`;
3. resolve the configured canonical knowledge path through existing typed
   configuration rather than reading a hard-coded path;
4. invoke the existing production ingestion service rather than duplicating discovery,
   loading, chunking, embedding or persistence logic;
5. close the Spring context on both success and failure;
6. terminate naturally after completion;
7. never start Tomcat;
8. never expose an administrative HTTP ingestion endpoint;
9. never truncate the database, drop tables or delete rows outside the corpus managed
   by the existing ingestion contract;
10. never implement a handwritten Ollama HTTP client.

Keep orchestration separate from the thin `main` method. The orchestration component
must be directly unit-testable without starting a real Ollama model or PostgreSQL.

## Machine-readable result

A successful run must emit exactly one final summary line beginning with:

`R4R_INGESTION_RESULT=`

The suffix must be valid compact JSON. It must contain verified values available from
the existing service contract, adapting names without inventing unsupported metrics.
At minimum expose:

- canonical knowledge path;
- discovered source count;
- indexed or changed source count;
- skipped or unchanged source count;
- deleted managed source count when the implementation supports deletion;
- persisted chunk count or the nearest exact equivalent;
- duration in milliseconds;
- success flag.

Do not print database passwords, model request payloads, embeddings or source content.

A failure must produce a concise error on stderr and a non-zero process exit. Separate:

- invalid configuration or missing corpus;
- unavailable model/database infrastructure;
- ingestion failure.

Do not use `System.exit` inside the testable orchestration component. A minimal `main`
adapter may map the returned code to process termination only when necessary.

## Configuration

Reuse the existing environment/property contract:

- `RAG_KNOWLEDGE_PATH`;
- `RAG_DB_URL`, `RAG_DB_USER`, `RAG_DB_PASSWORD`;
- `RAG_OLLAMA_BASE_URL`;
- `RAG_EMBEDDING_MODEL`;
- `RAG_EMBEDDING_DIMENSIONS`.

Do not change their meaning. Any new property must have a typed Spring configuration
binding, a safe default when appropriate, and focused tests.

## Deterministic tests

Create exactly:

`src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionCliTest.java`

Tests must prove:

1. the CLI delegates once to the existing ingestion boundary;
2. successful results are rendered as parseable JSON with the required stable prefix;
3. failure returns a non-zero result and does not print secrets;
4. the testable component does not require a live Ollama server or PostgreSQL;
5. the production entry point selects non-web mode;
6. normal `R4rSpringAiRagApplication` startup does not trigger ingestion.

Do not weaken or delete existing ingestion/pgvector tests.

## Acceptance matrix

| ID | Requirement | Required evidence |
|---|---|---|
| A1 | Dedicated CLI exists | Exact source inspection |
| A2 | Non-web and terminates | Focused test plus one bounded local invocation |
| A3 | Reuses ingestion service | Constructor/call verification; no duplicate pipeline |
| A4 | Stable JSON summary | JSON parsing assertions |
| A5 | No startup side effect | Context test proving normal web startup does not ingest |
| A6 | Existing behavior preserved | Clean full backend gate |

A generic green build is insufficient. Codex must inspect all six rows.

## Exact gate

Run exactly the command supplied by the controller. Do not add `tee`, pipes, redirects,
`grep`, `tail` or synthetic exit-code handling.

The gate performs a clean backend build and then requires
`KnowledgeIngestionCliTest` to exist and pass.

## Completion

Completion requires:

- exact gate exit code `0`;
- current source compilation, not stale `target/classes`;
- every acceptance row evidenced;
- changed paths inside backend ownership;
- Codex `ACCEPT`;
- automatic local commit with the task-plan message;
- no push.
