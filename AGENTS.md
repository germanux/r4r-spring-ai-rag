# R4R repository agent rules

## Purpose

Build the Java 21 Riansares 4R RAG incrementally. The controller owns task
selection, evidence, progress and commits. Codex plans/reviews read-only. OpenCode
implements exactly one selected task.

## Read order

Before any edit, read only:

1. `AGENTS.md`;
2. `.opencode/commands/task.md`;
3. `.opencode/memory.md`;
4. the selected task file;
5. companions whose filename starts with the selected task stem;
6. the current Codex instruction packet.

Do not read every task, historical run or knowledge document by default.

## Mandatory execution order

For every attempt:

1. run the exact task gate;
2. classify the failure and save the complete Maven output;
3. package only implicated source/config files into the runtime error bundle;
4. request a focused CodeGraph map when Java paths are implicated;
5. produce a concise read-only local understanding report;
6. let Codex inspect the complete log and packaged files;
7. edit only after Codex returns a bounded plan;
8. rerun the exact gate;
9. produce post-edit understanding and obtain Codex review.

An identical diagnostic may reuse a Codex plan during the configured cooldown.
Changed diagnostics bypass the cooldown. CodeGraph is advisory unless explicitly
configured as required; missing MCP evidence must not conceal compiler/test evidence.

## Product boundaries

- Keep the application non-web until an explicit task changes scope.
- No REST, Angular, Playwright, Testcontainers or browser automation in Tasks 01–04.
- Use Spring AI abstractions; no handwritten Ollama HTTP client.
- Keep deterministic loading, chunking, identities and tests independent of live LLMs.
- Do not add speculative layers, retries or background daemons to product code.

## PostgreSQL and tests

- PostgreSQL/pgvector runs through `docker-postgres/compose.yml`.
- `postgres-app` is persistent; `postgres-test` is disposable.
- Flyway exclusively owns application schema.
- Integration evidence must use real PostgreSQL/pgvector, never H2 or mocks.
- Use `./scripts/task-gate.sh <task>`; direct `mvn install` does not start the test DB.
- A refused connection to `127.0.0.1:55433` is infrastructure, not a Java defect.

## Editing discipline

- Edit only task-allowed paths.
- While compilation is red, repair one file/method at a time and compile after each
  bounded change.
- Do not replace a complete Java file when a method-level patch is sufficient.
- Keep package, imports, type declaration, fields, constructors, annotations and
  public signatures active.
- Temporary quarantine is allowed only inside one broken method body and must be
  removed before the official gate.
- Do not disable tests/plugins, weaken assertions or alter the gate to obtain green.

## Tool and Git discipline

- Run commands directly; no `bash -lc`, `tee`, synthetic success suffixes or hidden
  log redirection from OpenCode.
- No `sudo`, package managers, web search or external directories from OpenCode.
- No Git writes from OpenCode or Codex: no add, commit, reset, checkout, branch,
  worktree, merge, rebase, tag, clean or push.
- Runtime evidence belongs only under `runtime/runs/` and `runtime/control/`.

## Completion

A task is complete only when its exact gate is green and Codex returns `ACCEPT`.
The controller then updates progress/memory and may create a local commit. Pushes are
manual. Report exact changed paths, gate totals and the first unproven condition.
