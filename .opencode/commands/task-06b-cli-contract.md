# Task 06B — Finalize the CLI result and orchestration contract

## Ownership and timebox

PC/backend only. Target 45–70 minutes; hard ceiling 90 minutes.

## Objective

Make the thin CLI adapter and directly testable orchestration expose a stable result
contract without duplicating discovery, chunking, embedding or persistence.

## Required implementation

- Keep `main` thin and keep orchestration independently testable.
- Emit exactly one final line prefixed `R4R_INGESTION_RESULT=`.
- The suffix is compact valid JSON with canonical path, discovered/changed/unchanged
  counts, supported deletion count, persisted chunk count, duration and success.
- Map invalid configuration, unavailable infrastructure and ingestion failure to
  distinct non-zero outcomes without exposing secrets.
- Do not use a live Ollama model or PostgreSQL in the focused contract test.

Create or complete:

`src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionCliContractTest.java`

## Exact gate

`./scripts/task-gate.sh task-06b-cli-contract`

## Completion

Gate `0`, Codex `ACCEPT`, controller commit:

`feat(ingestion): finalize CLI result contract`
