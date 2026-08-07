# Task 06A — Stabilize the current production-ingestion CLI baseline

## Ownership and timebox

PC/backend only. Edit `src/main/**`, `src/test/**`, `docs/backend/**`, `pom.xml` and
`.env.example` only when directly required.

Target 45–70 minutes. Hard session ceiling: 90 minutes. This is a transition subtask:
preserve the useful implementation already present, make it commit-ready and do not
expand functionality.

## Objective

Produce the first clean, compilable checkpoint for the existing
`KnowledgeIngestionCli` work. Correct current compilation, malformed structure and
whitespace failures. Preserve the existing CLI, orchestration and result classes.

## Required evidence

- `KnowledgeIngestionCli` exists and selects non-web startup.
- `KnowledgeIngestionCliTest` exists and passes.
- Current source compiles from a clean `target/`.
- `git diff --check` and `git diff --cached --check` are clean.
- No frontend, controller, progress, memory or runtime file is edited.

Do not redesign exception classification, child-process proof or final integration in
this subtask; later subtasks own those concerns.

## Exact gate

`./scripts/task-gate.sh task-06-production-ingestion-cli`

## Completion

Gate exit `0`, controller validation, then controller commit:

`chore(ingestion): checkpoint compilable CLI baseline`
