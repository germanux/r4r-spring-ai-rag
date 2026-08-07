# R4R full-stack hierarchy and detailed task queues

> Temporary operational override: SURGICAL dispatch and mandatory review are disabled.
> Its branch and profiles remain for a later redesign. PC and LP continue on their
> independent queues; an exact green gate plus clean scope lets the deterministic
> controller close a task without `ACCEPT/REVISE`. Any older SURGICAL ownership or
> reviewer field below is historical and non-operative.

## Canonical decision

R4R has four roles but only three implementation levels. The experience figures are
calibration metaphors for autonomy and risk; they do not describe a model biography.

| Role | Calibration | Function | May edit code? |
|---|---:|---|---|
| RING | 10 years | Technical lead: decompose, classify, assign, hold, verify evidence | No |
| SURGICAL | 5 years | Temporarily disabled; retained for later redesign | No dispatch |
| PC | 2 years | Bounded developer for medium work in one component or layer | Yes, task scope only |
| LP | 6 months | Junior for small, prescriptive work packages | Yes, task scope only |

The retained SURGICAL branch is `agent/opencode-dual-surgical`, but Ring must not
dispatch it while the temporary override is active. Its former two-stage lane was:

1. `r4r-surgical-architect` performs read-only diagnosis and review.
2. `r4r-surgical-fixer` implements the smallest coherent level-3 correction.

Ring never repairs a defect itself. It emits a work package containing task ID, level,
owner, dependencies, exact `allowed_paths`, ordered steps, exact gate and required
review. The machine-readable authority is `.opencode/task-plan.hierarchy.json`.

## Level-selection rubric

### Level 1 — LP junior

Use level 1 only when all conditions hold:

- one observable behavior;
- one or two closely related files;
- prescribed implementation approach;
- no public contract or architecture choice;
- one exact gate;
- 15–35 minutes of model work.

Examples: add one DOM assertion, add one deterministic fixture, update a bounded
runbook section, prove an empty state, add a missing label.

### Level 2 — PC developer

Use level 2 when the work remains inside one component or layer but requires moderate
reasoning or several related files. It must still have one bounded outcome and one
exact gate. Target 30–60 minutes.

Examples: implement one backend test boundary, repair one component state transition,
add a dry-run mode with tests, integrate one configuration loader.

### Level 3 — SURGICAL Codex

Use level 3 for cross-layer behavior, controller lifecycle, branch/worktree handling,
concurrency, migrations, security boundaries, production process management or any
change whose incorrect implementation could corrupt evidence or other branches. Target
45–90 minutes; split it again if possible.

SURGICAL does not currently review level-1 or level-2 results. Ring coordinates
ownership and evidence while the deterministic controller enforces exact gates and
scope cleanliness.

## Non-breaking activation rule

The hierarchy plan is additive. Current execution and accepted-state migration still
use `.opencode/task-plan.backend.json` and `.opencode/task-plan.frontend.json` until
`OPS-HIER-02` through `OPS-HIER-04` pass. Never reopen accepted work or replace a live
progress file merely because the new catalog uses smaller work-package IDs.

## Historical parent-task classification

These tasks are accepted according to the current backend/frontend memories. The new
level is a retrospective classification, not authorization to rerun them.

| Parent task | Status | Recommended level | Role | Rationale |
|---|---|---:|---|---|
| `task-01-base` | Accepted | 2 | PC | Bounded build and baseline hygiene |
| `task-02-ingestion` | Accepted | 2 | PC | One backend ingestion layer |
| `task-03-pgvector` | Accepted | 3 | SURGICAL | Persistence plus infrastructure boundary |
| `task-04-rag` | Accepted | 3 | SURGICAL | Grounding, citation and abstention policy |
| `task-05-rag-api` | Accepted | 2 | PC | Bounded HTTP contract |
| `task-06-production-ingestion-cli` | Accepted | 2 | PC | CLI baseline |
| `task-06b-cli-contract` | Accepted | 2 | PC | One result/orchestration contract |
| `task-06c-spring-lifecycle` | Accepted | 3 | SURGICAL | Spring lifecycle and startup side effects |
| `task-06d-failure-classification` | Accepted | 2 | PC | Typed failures in one backend concern |
| `task-06e-child-process` | Accepted | 3 | SURGICAL | Real JVM lifecycle and termination |
| `task-fe-01-angular17-bootstrap` | Accepted | 2 | PC | Application/toolchain bootstrap |
| `task-fe-02-rag-client` | Accepted | 2 | PC | Typed HTTP client contract |
| `task-fe-03-rag-ui` | Accepted | 2 | PC | Component/template/state baseline |
| `task-fe-03b-answer-abstention` | Accepted | 1 | LP | Small prescribed rendered states |

## Pending backend sequence

### `task-06f-ingestion-validation` — currently REVISE

1. `BE-06F-A` — level 2 / PC.
   Resolve only the current unmerged evidence files, make `git diff --check` clean,
   preserve Flyway and `JdbcMetricsAutoConfiguration` exclusion, remove only
   `PgVectorStoreAutoConfiguration` from the test exclusion, then run
   `./scripts/task-gate.sh task-06f-ingestion-validation`.

2. `BE-06F-B` — level 2 / PC, after A.
   Capture the first current failure, apply one correction inside the ingestion
   validation layer, rerun the exact gate and stop. SURGICAL reviews the task-specific
   proof before closure.

### `task-07-populate-production-rag`

3. `BE-07-A` — level 1 / LP, only during backend phase.
   Document the required variable names and configurable Docker/database identities;
   never copy secret values.

4. `BE-07-B` — level 2 / PC.
   Run the full backend gate, launch the canonical CLI, query `vector_store`, prove a
   non-zero row count and retain exit/count evidence.

5. `BE-07-C` — level 1 / LP, only during backend phase.
   Record the baseline count, run identical ingestion again and fail when the count
   changes. This proves idempotence without redesigning ingestion.

### `task-08-rag-semantic-evaluation`

6. `BE-08-A` — level 1 / LP.
   Add deterministic supported-question, source-ID and unsupported-question fixtures.
   No live-model text assertions.

7. `BE-08-B` — level 2 / PC.
   Assert grounded retrieval and ordered structured citations using the fixtures.

8. `BE-08-C` — level 3 / SURGICAL.
   Define the abstention boundary without brittle model wording, reconcile retrieval
   thresholds with the public response contract, and run semantic plus full gates.

### `task-09-production-smoke`

9. `BE-09-A` — level 3 / SURGICAL.
   Build the packaged-application launch/readiness/log/termination harness. The exact
   child process must be stopped on success, failure and timeout.

10. `BE-09-B` — level 2 / PC.
    Exercise supported, cited, abstaining and invalid HTTP requests against that
    harness and assert stable JSON fields.

## Pending frontend sequence

### `task-fe-03c-citations` — currently REVISE

11. `FE-03C-A` — level 1 / LP.
    Edit only `rag-page.component.spec.ts`; add the three missing rendered-DOM
    assertions for citation count, stable source label/order and structured response
    fields. Run the exact FE-03C gate.

12. `FE-03C-B` — level 1 / LP, after A.
    Add one empty-citation fixture and prove it creates no placeholder list item.

### `task-fe-03d-dom-state-tests`

13. `FE-03D-A` — level 1 / LP.
    Use a controlled pending observable; prove loading text, disabled submit and
    recovery after completion.

14. `FE-03D-B` — level 2 / PC, during frontend phase.
    Prove error replacement, successful retry and reset clear answer, error and
    citations through rendered DOM.

### `task-fe-03e-security-accessibility`

15. `FE-03E-A` — level 2 / PC.
    Render hostile HTML-like answer and citation values; assert literal `textContent`,
    no injected element and no `innerHTML` escape hatch.

16. `FE-03E-B` — level 1 / LP.
    Add only missing labels, live-region semantics and keyboard-safe controls; avoid
    redundant ARIA and add focused DOM assertions.

### `task-fe-03f-final-validation`

17. `FE-03F-A` — level 2 / PC.
    Run whitespace preflight, the exact final Angular gate and first-failure-only
    correction. Remove generated output from the diff and stop for SURGICAL review.

### `task-fe-04-playwright`

18. `FE-04-A` — level 3 / SURGICAL.
    Define a deterministic Playwright harness using route interception or a bounded
    stub; it must not require a live LLM or external network and must clean processes.

19. `FE-04-B` — level 2 / PC.
    Implement happy path with ordered citations, abstention and backend-error flows;
    run the exact gate twice to expose hidden non-determinism.

## Operational and harness queue

20. `OPS-HIER-01` — level 3 / SURGICAL — establish this hierarchy, profiles, canonical
    routing plan and surgical model resolution. This design is the completed artifact;
    repository integration still requires review and commit.

21. `OPS-HIER-02` — level 3 / SURGICAL — add a first-class SURGICAL request lane. Ring
    may request it but must not launch it or edit code.

22. `OPS-HIER-03` — level 3 / SURGICAL — migrate progress by parent ID plus package ID;
    preserve all accepted states.

23. `OPS-HIER-04` — level 3 / SURGICAL — implement one shared configurable phase
    anchor; switch PC and LP together only between tasks, never mid-gate.

24. `OPS-MODEL-01` — level 3 / SURGICAL — repair the OpenCode provider/model resolver
    and remove invalid duplicated catalog entries without inventing IDs.

25. `OPS-MODEL-02` — level 2 / PC — create and test one non-secret common model loader
    used by Ring, SURGICAL, PC and LP.

26. `OPS-CONSOLE-01` — level 2 / PC — make Ring/PC/LP console selection independent of
    the caller's current working directory; test all three roots.

27. `OPS-GUARD-01` — level 3 / SURGICAL — distinguish liveness from productive
    progress, classify stalled output and stop unhealthy loops without log flooding.

28. `OPS-TOK-01` — level 2 / PC — record prompt, completion and total tokens for every
    run, without keys or prompt bodies in metrics.

29. `OPS-TOK-02` — level 3 / SURGICAL — enforce 64 KiB delta context, warning at
    80,000 tokens and stop at 120,000 tokens or 30 steps.

30. `OPS-SYNC-01` — ACCEPTED — remove `collect-agent-artifacts.py` and every
    invocation; keep runtime, progress, memory and `.opencode/current/` outside Git.

31. `OPS-SYNC-02` — level 2 / PC — add synchronization `--dry-run` coverage using
    temporary repositories.

32. `OPS-SYNC-03` — level 3 / SURGICAL — fail closed on dirty trees, overlapping
    write scopes and merge conflicts; never force-push or resolve conflicts silently.

33. `OPS-EVID-01` — ACCEPTED — enforce one writer and one
    `.ring-agent/evidence/<task>/<agent>-attempt-NN.md` path per semantic attempt.

34. `OPS-EVID-02` — level 1 / LP — document evidence naming and the absolute ban on
    versioning `runtime/**`.

35. `OPS-DOC-01` — level 1 / LP — update the operator playbook with exact start and
    console commands for Ring, SURGICAL, PC and LP.

36. `OPS-ACC-01` — level 2 / PC — execute the final model, Ring, console, Git,
    evidence and token acceptance matrix; SURGICAL reviews the evidence bundle.

## Dependencies and concurrency

- `BE-06F-A` precedes `BE-06F-B`; task 07 waits for task 06f acceptance.
- Task 08 waits for a deterministic task-07 corpus or explicit fixtures.
- Task 09 waits for semantic evaluation acceptance.
- `FE-03C-A` and B precede every PC frontend verification package.
- A SURGICAL package holds any PC/LP package whose `allowed_paths` overlap.
- PC and LP may run concurrently only when their canonical `allowed_paths` are
  disjoint and all dependencies are accepted.
- One worker, one task, one attempt file, one writer.

## Closure contract

Every active PC/LP package closes only when all are true:

1. dependencies are accepted;
2. diff is limited to canonical `allowed_paths`;
3. `git diff --check` is clean;
4. the exact package/parent gate is green;
5. evidence names the exact gate, exit code and changed paths;
6. the deterministic controller creates the closing commit;
7. Ring records the result but makes no code edit.

Generic build success, a checkpoint, a Ring recommendation or a model's narrative
claim is not acceptance evidence.
