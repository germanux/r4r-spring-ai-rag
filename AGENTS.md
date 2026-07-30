# R4R repository agent rules

## 1. Purpose

This repository builds a small, non-web Java 21 RAG application for Riansares 4R.
The implementation must remain incremental, test-driven and recoverable. The
machine-controlled task sequence is defined in `.opencode/task-plan.json`; the
human-readable parent task is `.opencode/commands/task.md`.

## 2. Mandatory read order

Before editing, read only:

1. `AGENTS.md`;
2. `.opencode/commands/task.md`;
3. `.opencode/memory.md`;
4. the exact task file selected by the controller;
5. the Codex plan supplied in the current prompt.

Do not read every task, every document or historical runtime log by default.

## 3. Responsibilities

- The Python controller selects tasks, runs deterministic gates, invokes Codex,
  validates scope, records evidence and creates accepted commits.
- Codex plans and reviews in read-only mode. It does not edit the repository.
- OpenCode implements one selected task. It does not select the next task.
- Maven, Flyway, PostgreSQL and tests determine acceptance. Model prose does not.

## 4. Product boundaries

- Keep the application non-web until a later explicit task changes that scope.
- Do not add REST controllers, Angular, Playwright, Testcontainers or a second
  orchestration framework.
- Use Spring AI abstractions. Do not create handwritten HTTP clients for Ollama.
- Prefer small cohesive classes and explicit contracts over speculative layers.
- Do not add retries, supervisors, worktrees or background daemons unless the
  active task explicitly requires them.

## 5. PostgreSQL and schema ownership

- PostgreSQL/pgvector runs only through `docker-postgres/compose.yml`.
- The development database is persistent under `docker-postgres/data/app/`.
- The integration-test database is disposable and uses tmpfs.
- Flyway is the sole owner of application schema changes.
- Container `init/` scripts must not duplicate application tables or migrations.
- Never replace PostgreSQL evidence with H2, mocks or an in-memory substitute.

## 6. Java and Spring rules

- Java release: 21.
- Preserve the non-web Spring Boot startup mode.
- Keep deterministic Markdown loading and chunking independent from live models.
- Preserve UTF-8, bounded input sizes, stable identities and idempotent behavior.
- Embedding dimensions, normalization and similarity policy must be explicit and
  consistent between indexing and querying.

## 7. CodeGraph

CodeGraph is available for structural impact analysis. Use it when a change spans
several symbols, callers or files. Do not use it as a ceremonial success gate and
do not replace source inspection or tests with CodeGraph output.

## 8. Shell and tool discipline

- Run commands directly; do not wrap them in `bash -lc`, `tee`, `tail`, pipelines
  or synthetic `echo success` suffixes unless the task explicitly needs that.
- Do not use `sudo`, package managers, web search or external directories from
  OpenCode.
- Do not edit task definitions, gate scripts, controller code or agent policy
  while implementing a product task.
- Do not run Git write commands from OpenCode: no add, commit, reset, checkout,
  clean, branch, worktree, merge, rebase, tag or push.

## 9. Scope and evidence

- Edit only paths allowed by the selected task.
- Run the exact task gate before claiming completion.
- Current command output is evidence; stale logs are not.
- Report the changed paths, exact gate result and first remaining unproven
  condition.
- All generated logs, decisions and evidence belong under
  `runtime/runs/<timestamp>/`; never scatter logs through the repository.

## 10. Git and completion

OpenCode must leave Git writes to the controller. After Codex returns `ACCEPT` and
the task gate is green, the controller updates progress and memory, then creates a
controlled commit when `R4R_AUTO_COMMIT=true`. Pushing is always manual.
