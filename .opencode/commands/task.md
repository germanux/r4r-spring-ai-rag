# R4R dual task queues

The launcher chooses an independent plan and progress file from `config/r4r-agents.json`.

## PC backend queue

`.opencode/task-plan.backend.json`: Java/Spring AI/PostgreSQL tasks. It may not edit
`frontend/**`.

## LP frontend queue

`.opencode/task-plan.frontend.json`: Angular 17 and Playwright tasks. It may edit only
`frontend/**` and `docs/frontend/**`.

Both controllers may run in the same working tree. Their runtime and progress files are
separate, peer product paths are ignored as background changes, and automatic Git
commits are disabled. Never launch two controllers for the same destination.
