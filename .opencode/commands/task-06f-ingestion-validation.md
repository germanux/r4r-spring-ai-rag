# Task 06F — Complete production-ingestion validation

## Ownership and timebox

PC/backend only. Target 45–70 minutes; hard ceiling 90 minutes.

## Objective

Run the complete backend proof for the CLI after Tasks 06A–06E. This is a validation
subtask, not a feature-development task.

## Required evidence

- Full backend gate is green from a clean `target/`.
- All focused CLI contract, lifecycle, failure-classification and child-process tests
  are green.
- Existing ingestion, pgvector, RAG and HTTP API behavior remains green.
- `git diff --check` is clean.
- No unsupported metric, startup side effect, live-LLM dependency or secret output
  remains.

Make only the smallest correction directly proven by the first failing gate.

## Exact gate

`./scripts/task-gate.sh task-06f-ingestion-validation`

## Completion

Gate `0`, Codex `ACCEPT`, controller commit:

`test(ingestion): complete production CLI validation`
