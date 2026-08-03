# R4R dual task queues

The launcher chooses an independent plan and progress file from `config/r4r-agents.json`.

## PC backend queue

`.opencode/task-plan.backend.json`: Java/Spring AI/PostgreSQL tasks. It may not edit
`frontend/**`.

## LP frontend queue

`.opencode/task-plan.frontend.json`: Angular 17 and Playwright tasks. It may edit only
`frontend/**` and `docs/frontend/**`.

Both controllers use separate progress, memory, control and runtime evidence. Peer
product paths are background-only. OpenCode/Qwen3 and Codex never write Git history;
the deterministic controller may create a gate-green checkpoint and a final ACCEPT
commit. A checkpoint does not complete the task. Never launch two controllers for the
same destination.
