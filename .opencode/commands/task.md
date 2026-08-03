# R4R dual task queues

The launcher chooses an independent plan and progress file from
`config/r4r-agents.json`.

## Queue ownership

- **PC/backend** uses `.opencode/task-plan.backend.json` and may not edit
  `frontend/**`.
- **LP/frontend** uses `.opencode/task-plan.frontend.json` and may edit only
  `frontend/**` and `docs/frontend/**`.
- Both controllers keep separate progress, memory, control and runtime evidence.
  Never launch two controllers for the same destination.

## Subtask contract

Each plan item is a commit-sized subtask:

1. one bounded objective;
2. one exact deterministic gate;
3. one owned path set;
4. one Codex decision;
5. one controller-owned closing commit.

Target 45–70 minutes of useful model work. The hard session ceiling is 90 minutes
(`5400` seconds). A session timeout preserves evidence and the working tree; it does
not mark the task complete.

A task that combines independent concerns must be split before execution. Typical
separate concerns are CLI contract, Spring lifecycle, typed failure classification,
real child-process proof, DOM behavior, citations, accessibility and final validation.

## Commit progression

OpenCode/Qwen3 and Codex never write Git history. The deterministic controller:

1. runs the exact gate;
2. rejects whitespace errors before expensive work;
3. creates a task-scoped checkpoint when the gate is green and a product diff exists;
4. requests Codex review;
5. creates the closing commit only after gate `0` and Codex `ACCEPT`;
6. advances to the next pending subtask.

A checkpoint preserves useful compilable work but does not complete the task.
