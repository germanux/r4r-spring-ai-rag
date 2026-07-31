# R4R task sequence

The controller executes `.opencode/task-plan.json` in order:

1. `task-01-base` — Java/PostgreSQL/Flyway/Spring AI baseline;
2. `task-02-ingestion` — deterministic idempotent Markdown ingestion;
3. `task-03-pgvector` — Spring AI PgVector persistence/retrieval;
4. `task-04-rag` — cited non-web RAG with abstention.

For each task it runs the exact gate, creates a full diagnostic/error bundle, requests
focused CodeGraph evidence when useful, obtains a local understanding summary, asks
Codex for a read-only plan, lets OpenCode edit, reruns the gate and asks Codex to
review. Only gate green plus `ACCEPT` advances progress.

Do not rename tasks, edit progress manually, infer success from a generic build or
run two controllers against the same worktree.
