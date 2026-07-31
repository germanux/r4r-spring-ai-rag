# R4R agent rules

## Roles

The Python controller selects the task, runs gates, stores evidence and may commit.
Codex plans/reviews read-only. OpenCode edits only the active task.

Active-task lock files are disabled. Resume ownership comes from
`.opencode/progress.json`; stale `runtime/locks/active-task.json` is ignored/deleted.
Controller/config files are maintenance and do not block a task resume.

## Read order

Read only `AGENTS.md`, `.opencode/commands/task.md`, `.opencode/memory.md`, the
active task, its same-stem companions and the current Codex packet. Do not ingest
historical runtime logs or unrelated tasks unless diagnostics name them.

## Attempt order

1. Run the exact task gate.
2. Classify the first current failure and retain the full log.
3. Inspect only implicated files and direct CodeGraph callers.
4. Follow the bounded Codex plan.
5. Edit one coherent batch; do not rewrite unrelated classes.
6. Re-run the exact gate and hand evidence to Codex.
7. Stop after two identical tool failures or three no-progress cycles.

## Product boundaries

- Tasks 01–04 remain Java 21, Spring AI, Flyway and PostgreSQL/pgvector.
- No REST/frontend/browser code in those tasks.
- No handwritten Ollama HTTP client or live LLM dependency in deterministic tests.
- Flyway owns schema; integration tests use the disposable real PostgreSQL service.
- `127.0.0.1:55433 refused` is infrastructure, not a Java defect.

## Editing and Git

Edit only active-task product paths. Controller/config maintenance under `scripts/`,
`py-codex-agent/`, `.opencode/`, `AGENTS.md`, `opencode.jsonc`, `.env*`,
`codegraph.json` and `.gitignore` is outside product scope and must not block resume.

While compilation is red, fix the first compiler error before broadening work. Do not
disable tests, weaken assertions, alter gates, use sudo or run package managers.
OpenCode and Codex never write Git history. The controller may create local commits;
pushes remain manual.

## Completion

A task completes only with its exact gate green and Codex `ACCEPT`. Report changed
paths, exact gate exit, test totals and the first unproven condition.
