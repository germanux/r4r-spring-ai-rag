# R4R dual-agent rules

## Roles and queues

Two product controllers may run concurrently because ownership is disjoint:

- **PC / backend** uses `.opencode/task-plan.backend.json` and owns Java 21,
  Spring AI, PostgreSQL/pgvector and backend documentation.
- **LP / frontend** uses `.opencode/task-plan.frontend.json` and owns
  `frontend/**` and `docs/frontend/**`; Angular must remain major 17.
- **RING / director** coordinates both queues and may read or modify any file in the
  Ring worktree when a current evidence-backed cross-cutting correction is necessary.
  This Ring exception includes this `AGENTS.md` file and other policy/configuration
  files.

The canonical runtime/model configuration is `config/r4r-agents.json`. Machine-local
endpoints belong in `.env.r4r.local`, never in the application `.env`.

## Subtask size, timebox and commits

These rules apply to **PC**, **LP** and **RING**. For Ring, a task means one bounded,
evidence-backed cross-cutting correction in the Ring worktree rather than an unbounded
repository rewrite.

- An agent task should represent one bounded outcome that can normally be completed
  in 45–70 minutes of useful model work.
- The hard OpenCode session ceiling is 90 minutes (`5400` seconds). Reaching the
  ceiling stops the session; it does not authorize a broader scope or an unreviewed
  commit.
- Every subtask has one objective, one exact gate and one controller-owned closing
  commit. A subtask may create an earlier gate-green checkpoint, but the closing
  commit still requires Codex `ACCEPT`.
- Split work again when one task mixes independent concerns such as entrypoint,
  lifecycle, exception classification, subprocess proof, DOM behavior, accessibility
  or final integration validation.
- Before an expensive gate, the deterministic gate must reject whitespace errors with
  `git diff --check`; do not spend another full Maven or Angular cycle on a patch that
  cannot be committed.
- After a subtask is accepted and committed, advance immediately to the next pending
  subtask. Do not reopen accepted work without a current regression.

## Ring non-destructive authority

- Ring may modify existing repository files, including Java, Angular, scripts,
  controller code, documentation and agent instructions.
- Ring must never delete, move or rename an existing file or directory.
- Ring must never truncate an existing file to empty or replace useful content with a
  placeholder.
- Ring reads before editing and preserves unrelated content.
- New files belong in an appropriate existing directory; the repository root is
  reserved for canonical project entry files.
- Ring does not edit secrets, credentials, private keys, tokens, `.env` files or
  runtime PID/lock files.
- Ring does not write Git history. The deterministic controller or the human operator
  owns commits and pushes.

## Concurrency

- Never run two workers for the same queue.
- Peer-owned product paths are tolerated as concurrent background changes but are not
  writable by the PC or LP peer agent.
- Each worker has separate progress, memory, control and run directories.
- OpenCode/Qwen3 and Codex never write Git history.
- The deterministic Python controller may create a task-scoped checkpoint immediately
  after the exact gate is green, and a closing commit after Codex `ACCEPT`.
- A checkpoint preserves useful compilable work but does not mark the task accepted.
- Do not run `git add`, `git commit`, `git reset`, `git checkout`, `git merge` or
  `git push` from a model/tool session; only the controller owns automated commits.

## Code intelligence

- `npm run repos:sync` materializes repositories declared in
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
- Never recursively search generated or runtime-heavy directories:
  `frontend/node_modules/**`, `frontend/dist/**`, `frontend/.angular/**`,
  `node_modules/**`, `runtime/**` and `.r4r/**`.
- Concise observability reports under `runtime/runs/**/decisions/*.md` and
  `runtime/runs/**/evidence/*.md` are versioned. Inspect only the exact task/run
  needed; logs, JSON diagnostics, patches, PID/lock files and bulk runtime output
  remain local.

