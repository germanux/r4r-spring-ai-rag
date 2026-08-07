# R4R OpenCode engineering system

## Logical agents

R4R has three persistent logical agents:

- **RING** coordinates only. It uses `openai/gpt-5.6-luna`, reads bounded evidence,
  generates assignments and never implements code.
- **PC** is a fullstack worker. It uses `openai/gpt-5.6-terra` and executes exactly
  one Ring-generated assignment at a time.
- **LP** is a fullstack worker with the same capabilities and model as PC. Its separate
  worktree, progress, memory, runtime and Git identity remain isolated.

A fourth OpenCode profile, **ESCALATION**, is invoked only when Ring marks a decision
`ESCALATE`. It uses `openai/gpt-5.6-sol`, is read-only outside staged Ring output,
and must return a complete replacement decision. It is not a persistent worker or an
independent task queue.

The only task source is `.opencode/task-plan.json`. Ring generates current assignments
under `runtime/control/PC/assignment.json` and
`runtime/control/LP/assignment.json`. Legacy backend/frontend/hierarchy plans are
historical and must not be used by runtime code.

The only runtime/model configuration is `config/r4r-agents.json`. Machine-local
credentials belong outside the repository or in ignored local environment files.

## Assignment and concurrency rules

- PC and LP are fullstack peers; task domain does not determine ownership.
- Ring assigns by current evidence, dependencies, capacity and disjoint write scopes.
- Each worker may have at most one active assignment.
- A worker never scans the plan for the next pending task. It remains quiescent without
  a fresh `START`, `CONTINUE` or `RETRY_AUTHORIZED` assignment.
- Ring must never publish overlapping `allowed_paths` to PC and LP.
- The task's `allowed_paths` is the only write scope. Do not create a second scope
  field that can drift.
- Each worker has separate progress, memory, control, run and evidence paths.
- Runtime, progress, memory and `.opencode/current/**` are generated local state and
  remain ignored.

## OpenCode-only execution

All model sessions run through OpenCode. Production scripts and Python packages must
not invoke or import the Codex CLI/controller. The historical implementation is stored
under `docs/archive/py-codex-agent/` for reference only and is not importable at
runtime.

OpenCode sessions never write Git history. The deterministic `r4r_worker` controller
inside `py-ring-agent` owns validation, checkpointing and final commits. Ring and the
Sol escalation never commit or launch workers.

The session limits are:

- 64 KiB maximum context delta;
- warning at 80,000 tokens;
- stop at 120,000 tokens or 30 steps;
- 90-minute wall-clock ceiling;
- one deterministic recovery grant per blocked task.

## Worker protocol

For every assignment:

1. Validate the fresh assignment, exact task ID and exact write scope.
2. Read `AGENTS.md`, the canonical plan entry, task command and worker memory.
3. Run the exact gate before editing.
4. Classify the first current failure and retain full diagnostics.
5. Use focused CodeGraph or Code-Graph-RAG retrieval when it materially helps.
6. Edit one coherent batch inside the assigned `allowed_paths`.
7. Re-run the exact gate.
8. Let the deterministic controller checkpoint or commit only when scope and gate are
   clean.
9. Stop after that assignment. Ring decides what comes next.

A `CONTINUE` assignment never unlocks a `BLOCKED` task.
`RETRY_AUTHORIZED` permits one additional attempt only when its unconsumed
authorization ID, expiry, task and scope all match. A second failed recovery returns to
`HOLD`.

## Git and evidence

- Do not run `git add`, `git commit`, `git reset`, `git checkout`, `git merge`
  or `git push` from a model session.
- The controller rejects out-of-scope product changes and non-fast-forward history.
- Peer commits may advance the shared integration history only when their paths are
  outside the current assignment.
- Before an expensive gate, reject staged and unstaged whitespace errors.
- Every semantic Ring decision writes one durable summary under
  `.ring-agent/evidence/<task-id>/` with one writer and one exclusive evidence path.
- Polling timestamps, regenerated runtime paths and unchanged evidence never justify a
  coordination commit.
- Binary bundles, full logs, PID files and locks remain in ignored runtime storage.

## Product boundaries

- Backend work uses Java 21, Spring Boot and Spring AI abstractions; never add a
  handwritten Ollama HTTP client.
- Flyway owns the application schema.
- Frontend uses Angular 17 strict mode.
- Unit and Playwright tests must not require a live LLM.
- PostgreSQL runs in Docker for project gates.
- Reference repositories and code graphs are read-only evidence, never edit targets.
- Never recursively search generated or heavy paths:
  `frontend/node_modules/**`, `frontend/dist/**`, `frontend/.angular/**`,
  `node_modules/**`, `target/**`, `runtime/**`, `.r4r/**` and `.git/**`.

A task is accepted only when its exact deterministic gate is green, its assigned scope
is clean and the controller records the result.
