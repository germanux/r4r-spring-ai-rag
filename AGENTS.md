# R4R dual-agent rules

## Roles and queues

Two controllers may run concurrently in the same working tree because ownership is
disjoint:

- **PC / backend** uses `.opencode/task-plan.backend.json` and may edit Java 21,
  Spring AI, PostgreSQL/pgvector and backend documentation.
- **LP / frontend** uses `.opencode/task-plan.frontend.json` and may edit only
  `frontend/**` and `docs/frontend/**`; Angular must remain major 17.

The canonical runtime/model configuration is `config/r4r-agents.json`. Machine-local
endpoints belong in `.env.r4r.local`, never in the application `.env`.

## Concurrency

- Never run two workers for the same queue.
- Peer-owned product paths are tolerated as concurrent background changes but are not
  writable by the current agent.
- Each worker has separate progress, memory, control and run directories.
- OpenCode/Qwen3 and Codex never write Git history.
- The deterministic Python controller may create a task-scoped checkpoint immediately
  after the exact gate is green, and a closing commit after Codex `ACCEPT`.
- A checkpoint preserves useful compilable work but does not mark the task accepted.
- Do not run `git add`, `git commit`, `git reset`, `git checkout`, `git merge` or
  `git push` from a model/tool session; only the controller owns automated commits.

## Code intelligence

- `npm run repos:sync` materializes the repositories declared in
  `knowledge/code-repositories.md` under the ignored `.r4r/` directory.
- `npm run code:index` indexes the application and enabled references with CodeGraph
  and Code-Graph-RAG.
- CodeGraph/Code-Graph-RAG are retrieval tools. Agents must not invoke destructive
  graph wipe/index tools or their file-writing tools; indexing is performed by npm.
- Reference repositories are read-only evidence, never product-edit targets.

## Attempt order

1. Run the selected queue's exact gate.
2. Classify the first current failure and retain the full evidence.
3. Use focused CodeGraph/Code-Graph-RAG retrieval when useful.
4. Follow the bounded Codex plan.
5. Edit one coherent batch inside the current worker's allowed paths.
6. Re-run the exact gate. When green, let the controller write worker memory and a
   task-scoped checkpoint before handing the same evidence to Codex.
7. Stop on scope/Git violations and bounded-session watchdog triggers; retry only with
   a changed plan or new evidence.

## Product boundaries

- Backend tasks use Spring AI abstractions; no handwritten Ollama HTTP client.
- Frontend tests and Playwright must not require a live LLM.
- Flyway owns the backend schema.
- A task completes only with its exact gate green and Codex `ACCEPT`.
  - Never run find, rg, grep or glob recursively through:
    frontend/node_modules/**
    frontend/dist/**
    frontend/.angular/**
    node_modules/**
    runtime/**
    .r4r/**
