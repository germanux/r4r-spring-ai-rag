# Agent memory

## Current state

- Worker: PC.
- Run: 20260806T190420Z.
- Last accepted task: task-06f-ingestion-validation.
- Active task: task-07-populate-production-rag.
- Current attempt: 1.
- Latest exact gate: task-gate; exit=0.
- Latest Codex decision: pending.
- Checkpoint: pending; head=not recorded.
- Accepted: task-01-base, task-02-ingestion, task-03-pgvector, task-04-rag, task-05-rag-api, task-06-production-ingestion-cli, task-06b-cli-contract, task-06c-spring-lifecycle, task-06d-failure-classification, task-06e-child-process, task-06f-ingestion-validation.
- Remaining: task-07-populate-production-rag, task-08-rag-semantic-evaluation, task-09-production-smoke.
- Exact plan: `.opencode/task-plan.backend.json`.

## Files currently owned or edited

- `docs/backend/production-ingestion-evidence.md`
- `src/main/java/com/riansares/r4r/ingestion/KnowledgeIngestionService.java`
- `src/main/java/com/riansares/r4r/vector/PgVectorKnowledgeStore.java`
- `src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionServiceIT.java`
- `src/test/java/com/riansares/r4r/ingestion/TestChildApplicationContextInitializer.java`

## Demonstrated by current evidence

- The exact deterministic task gate completed with exit code 0.
- The checkpoint contains only task-owned product paths plus controller progress/memory.

## Still unproven or below expectations

- Codex has not yet accepted the current checkpoint.

## Approaches not to repeat

- Do not repeat an unchanged failing action without new evidence.

## Next exact action

Preserve a deterministic gate-green checkpoint, generate final evidence and request Codex review.

## Fixed decisions

- OpenCode/Qwen3 and Codex never write Git history.
- The deterministic Python controller may create a gate-green checkpoint and a final ACCEPT commit.
- A gate-green checkpoint preserves useful work but does not mark the task ACCEPTED.
- A task completes only after its exact gate is green and Codex returns `ACCEPT`.
- PostgreSQL only in Docker; Flyway owns application schema.
- Spring AI abstractions; no handwritten Ollama HTTP client.
- Every red gate retains complete diagnostics for Codex.
- CodeGraph is focused retrieval evidence, not authority to expand task scope.
- Markdown/JSON activity is published under `.opencode/current/{ring,PC,LP}/`; raw runtime is never committed directly.

## Task ledger

- task-01-base: ACCEPTED — accepted at 2026-07-30T01:56:21.671118+00:00; last green attempt=none
- task-02-ingestion: ACCEPTED — accepted at 2026-07-30T14:21:45.327732+00:00; last green attempt=none
- task-03-pgvector: ACCEPTED — accepted at 2026-07-31T04:57:58.481883+00:00; last green attempt=none
- task-04-rag: ACCEPTED — accepted at 2026-07-31T17:08:50.489886+00:00; last green attempt=none
- task-05-rag-api: ACCEPTED — accepted at 2026-07-31T18:58:37.474302+00:00; last green attempt=none
- task-06-production-ingestion-cli: ACCEPTED — accepted at 2026-08-03T23:52:33.915790+00:00; last green attempt=1
- task-06b-cli-contract: ACCEPTED — accepted at 2026-08-04T04:15:14.759540+00:00; last green attempt=3
- task-06c-spring-lifecycle: ACCEPTED — accepted at 2026-08-04T07:30:03.038079+00:00; last green attempt=4
- task-06d-failure-classification: ACCEPTED — accepted at 2026-08-04T08:15:06.995514+00:00; last green attempt=1
- task-06e-child-process: ACCEPTED — accepted at 2026-08-05T20:55:14.801213+00:00; last green attempt=1
- task-06f-ingestion-validation: ACCEPTED — accepted at 2026-08-06T01:33:53.234680+00:00; last green attempt=1
- task-07-populate-production-rag: BLOCKED — accepted at not accepted; last green attempt=1
- task-08-rag-semantic-evaluation: PENDING — accepted at not accepted; last green attempt=none
- task-09-production-smoke: PENDING — accepted at not accepted; last green attempt=none
