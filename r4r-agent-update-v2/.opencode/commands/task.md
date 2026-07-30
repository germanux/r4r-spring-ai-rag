# R4R task sequence

This is the parent task for the repository. The automatic controller executes the
ordered subtasks from `.opencode/task-plan.json`:

1. `task-01-base.md` — green Java/PostgreSQL/Flyway/Spring AI baseline;
2. `task-02-ingestion.md` — deterministic, idempotent document ingestion;
3. `task-03-pgvector.md` — Spring AI PgVector persistence and retrieval;
4. `task-04-rag.md` — minimal cited, non-web RAG service with abstention.

The controller performs this cycle automatically:

1. verify previously accepted tasks;
2. select the first pending or regressed task;
3. ask Codex for a read-only structured plan;
4. synchronize CodeGraph and force a verified read-only `codegraph_*` reconnaissance;
5. run OpenCode only on that task using the structural report;
6. execute the deterministic task gate;
7. ask Codex for a structured review that includes CodeGraph evidence;
8. request bounded revisions when required;
9. update `.opencode/progress.json` and `.opencode/memory.md`;
10. create a controlled local commit when enabled;
11. continue with the next task until complete or genuinely blocked.

Do not manually rename the active task, advance progress or infer acceptance from a
green generic build. Each subtask has its own deterministic gate.
