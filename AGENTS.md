# R4R four-role engineering hierarchy

## Roles, experience calibration and queues

The years below are an operational calibration for autonomy and risk, not a factual
claim about a model's biography. There are three implementation levels and one
coordination role:

- **RING / technical lead — 10-year calibration, no coding.** Ring decomposes work,
  classifies risk, assigns one owner, checks dependencies and decides whether evidence
  is sufficient. It writes only staged coordination outputs. Ring never edits product,
  test, script, controller, configuration or policy code.
- **SURGICAL / senior developer and reviewer — 5-year calibration, level 3.** Codex
  runs through OpenCode using the `r4r-surgical-architect` and
  `r4r-surgical-fixer` profiles on branch `agent/opencode-dual-surgical`. It implements
  cross-cutting or high-risk work and reviews every PC and LP result before closure.
- **PC / developer — 2-year calibration, level 2.** PC performs bounded medium-risk
  implementation within the active backend or frontend phase. It does not make
  repository-wide architecture, lifecycle or Git-synchronization decisions.
- **LP / junior developer — 6-month calibration, level 1.** LP receives small,
  prescriptive work packages with exact files, one observable behavior and one gate.
  It never invents architecture, widens scope or resolves cross-component ambiguity.

The canonical classification is `.opencode/task-plan.hierarchy.json`. The active
legacy backend/frontend plans remain execution-state authorities until phase-aware
migration is implemented.

The canonical runtime/model configuration is `config/r4r-agents.json`. Machine-local
endpoints belong in `.env.r4r.local`, never in the application `.env`.

## Subtask size, timebox and commits

These rules apply to all implementation work. Ring creates and routes work packages;
it does not implement them.

- A level-1 LP package targets 15–35 minutes, one or two closely related files and one
  exact assertion or visible behavior.
- A level-2 PC package targets 30–60 minutes, one component or layer and one exact gate.
- A level-3 SURGICAL package targets 45–90 minutes and may cross layers only when the
  work cannot safely be decomposed further.
- The hard OpenCode session ceiling is 90 minutes (`5400` seconds). Reaching the
  ceiling stops the session; it does not authorize a broader scope or an unreviewed
  commit.
- Every subtask has one objective, explicit dependencies, one canonical `allowed_paths`
  write scope, one exact gate and one controller-owned closing commit. A subtask may
  create an earlier gate-green checkpoint, but closure still requires SURGICAL Codex
  `ACCEPT` through OpenCode.
- Split work again when one task mixes independent concerns such as entrypoint,
  lifecycle, exception classification, subprocess proof, DOM behavior, accessibility
  or final integration validation.
- Before an expensive gate, the deterministic gate must reject whitespace errors with
  `git diff --check`; do not spend another full Maven or Angular cycle on a patch that
  cannot be committed.
- After a subtask is accepted and committed, advance immediately to the next pending
  subtask. Do not reopen accepted work without a current regression.

## Ring coordination-only authority

- Ring may read repository and runtime evidence required to classify work.
- Ring may write only the exact staged outputs under the supplied `OUTPUT_DIR`.
- Ring never edits Java, Angular, tests, scripts, controller code, configuration,
  documentation, task plans, agent profiles or `AGENTS.md`.
- Ring never deletes, moves, renames or truncates repository content.
- Ring never writes Git history, launches workers or applies a SURGICAL patch.
- When Ring identifies a code or policy correction, it creates a bounded level-1,
  level-2 or level-3 work package and routes it to LP, PC or SURGICAL respectively.

## Concurrency

- Never run two workers for the same queue.
- Every task's `allowed_paths` entry is its canonical `write_scope`; do not create a
  second scope field that can drift from controller enforcement.
- Before publishing directives, Ring validates active task IDs against the configured
  plans and rejects dispatch when write scopes overlap. A level-3 SURGICAL task holds
  overlapping PC/LP work until the surgical patch is integrated and revalidated.
- Peer-owned product paths are tolerated as concurrent background changes but are not
  writable by the PC or LP peer agent.
- Each worker has separate progress, memory, control and run directories.
- OpenCode/Qwen3 and OpenCode/Codex never write Git history.
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
4. Follow the bounded Ring work package; escalate ambiguity to SURGICAL instead of
   widening scope.
5. Edit one coherent batch inside the current worker's allowed paths.
6. Re-run the exact gate. When green, let the controller write worker memory and a
   task-scoped checkpoint before handing the same evidence to SURGICAL Codex through
   OpenCode.
7. Stop on scope/Git violations and bounded-session watchdog triggers; retry only with
   a changed plan or new evidence.

## Product boundaries

- Backend tasks use Spring AI abstractions; no handwritten Ollama HTTP client.
- Frontend tests and Playwright must not require a live LLM.
- Flyway owns the backend schema.
- A level-1 or level-2 task completes only with its exact gate green and SURGICAL
  Codex `ACCEPT`. A level-3 task requires its exact gate, a read-only surgical review
  pass and controller acceptance of the emitted patch.
- Never recursively search generated or runtime-heavy directories:
  `frontend/node_modules/**`, `frontend/dist/**`, `frontend/.angular/**`,
  `node_modules/**`, `runtime/**` and `.r4r/**`.
- `runtime/` is ephemeral working state and is globally ignored. Inspect only the
  exact task/run needed.
- Ring publishes one durable Markdown summary per task, agent/model and attempt under
  `.ring-agent/evidence/<task-id>/`. Each file has one writer, and its task-derived
  `write_scope` is recorded in the summary and worker directive. The directive also
  records `assigned_agent`, `model`, `branch` and the exclusive `evidence_path`.
- Binary bundles, patches, PID/lock files and bulk text logs remain only in ignored
  runtime storage; they are never copied into `.ring-agent/evidence/`.
