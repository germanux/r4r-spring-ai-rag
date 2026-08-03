# Task 06E — Prove the real production CLI process

## Ownership and timebox

PC/backend only. Target 45–70 minutes; hard ceiling 90 minutes.

## Objective

Execute the actual `KnowledgeIngestionCli` as a bounded child JVM and prove its
observable process contract.

## Required evidence

- The child command names `KnowledgeIngestionCli`, not the normal web application.
- The child receives a bounded temporary corpus and explicit `RAG_*` configuration.
- Success exits `0`, emits one parseable result line and terminates without Tomcat.
- A deterministic failure exits non-zero and does not leak secrets.
- The process has a hard timeout and is always destroyed on test cleanup.
- The proof must not require a live external LLM.

Create or complete:

`src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionCliProcessIT.java`

## Exact gate

`./scripts/task-gate.sh task-06e-child-process`

## Completion

Gate `0`, Codex `ACCEPT`, controller commit:

`test(ingestion): verify production CLI process`
