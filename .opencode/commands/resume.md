Resume the automatic task cycle.

1. Read `AGENTS.md`, `.opencode/commands/task.md` and `.opencode/memory.md`.
2. Read only the task referenced by `runtime/locks/active-task.json` when that file
   exists; otherwise use the first pending task in `.opencode/progress.json`.
3. Do not select or mark a later task yourself.
4. Do not run Git writes.
5. Finish at the selected task gate and report exact evidence.

Normal continuation should use `./scripts/run-codex-agent.sh`; this command exists
only for manual inspection or recovery.
Do not use @SpyBean, Mockito, AOP, reflection, subclassing, or a production failure hook.

In KnowledgeIngestionServiceIT, inject the Spring-managed KnowledgeIngestionService.
Configure one stable temporary knowledge root for the Spring test context.

Prove transactional rollback using PostgreSQL:
1. ingest original content;
2. capture the exact source checksum and ordered chunk rows;
3. create a temporary BEFORE INSERT trigger on knowledge_chunks that raises an exception;
4. change the source Markdown;
5. call the Spring-managed ingestionService.ingest() and assert IllegalStateException;
6. drop the trigger in finally;
7. assert the original checksum and exact ordered chunks remain unchanged.

Run ./scripts/task-gate.sh task-02-ingestion and stop.

Please be disciplined, check the list of instructions and lockers provided to you.
