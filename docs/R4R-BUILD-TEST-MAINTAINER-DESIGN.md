# R4R build/test maintainer — implementation design

## Objective

Create a restrained sibling worker that runs at most once every two hours and performs
only two sequential recovery phases on the integration branch:

1. **compile phase** — make backend and frontend compile, then create one controller-
   owned commit;
2. **test phase** — make deterministic tests pass without changing product behaviour
   beyond what the failing tests require, then create a second controller-owned commit.

It must not start feature work, refactor unrelated code, update dependencies without a
failure proving it is necessary, or mark PC/LP tasks accepted.

## Existing infrastructure to reuse

- `RingCommandFile` already supports workers `RING`, `PC`, `LP`, `MAINTAINER`.
- Operator commands already include `stop`, `pause`, `continue` and `restart`.
- The shared command file is `runtime/the-ring-command.jsonc`.
- The Python controller already owns deterministic commits; Codex and OpenCode remain
  forbidden from writing Git history.
- Ring already publishes advisory worker directives after validated staged output.

## Proposed worker identity

Use a new product-maintenance identity distinct from the existing harness maintainer:

```text
BUILD_MAINTAINER
agent/build-maintainer
runtime/control/BUILD_MAINTAINER/
runtime/runs/BUILD_MAINTAINER/
.opencode/memory.build-maintainer.md
```

Do not overload the current `MAINTAINER`, which repairs the agent harness in an
isolated temporary worktree.

## Scheduling

Use a systemd user timer rather than cron:

```text
OnBootSec=20min
OnUnitActiveSec=2h
Persistent=true
```

The service obtains a non-blocking repository lock and exits successfully when:

- Ring/merge synchronization is active;
- PC or LP currently owns a live task mutation;
- the integration worktree is dirty outside an explicitly adopted maintenance scope;
- the previous build-maintainer run is still active;
- fewer than two hours have elapsed since the last completed run.

## Phase 1 — compile-only commit

Backend gate:

```bash
./mvnw -DskipTests package
```

or the repository's exact compile command when different.

Frontend gate:

```bash
npm --prefix frontend run build
```

The editing prompt may change only files implicated by compiler output. Tests are not
edited in this phase. After both compile gates are green, the deterministic controller
stages only owned paths, runs `git diff --cached --check`, and commits:

```text
fix(build-maintainer): restore backend and frontend compilation
```

No commit is created when there is no product diff.

## Phase 2 — tests-only recovery commit

Run the exact backend and frontend deterministic test gates. The prompt receives:

- current failure logs;
- the compile commit SHA;
- current PC/LP task ownership;
- Ring advisory evidence;
- a strict prohibition on weakening, deleting or skipping tests.

Product code may be modified only when a failing test demonstrates a real defect. Test
files may be corrected when they are stale, false-positive or incompatible with the
accepted contract. After all deterministic tests are green, commit:

```text
fix(build-maintainer): restore deterministic test suite
```

## Safety and merge policy

- Operate in a dedicated linked worktree from `agent/integration`.
- Rebase/fetch before starting; never force-push.
- Maximum one compile commit and one test commit per run.
- Maximum two OpenCode correction cycles per phase.
- Target 45–70 minutes of useful model work per run; hard OpenCode session ceiling
  90 minutes (`5400` seconds).
- No dependency upgrades, migrations, schema changes or broad formatting unless the
  failure directly requires them.
- Never modify progress files to mark tasks accepted.
- Abort on secrets, generated files, scope ambiguity or unrelated dirty paths.
- Export evidence and SHA-256 for every run.

## Signalling siblings

After a successful phase, write an atomic request through `RingCommandFile` rather than
editing JSONC text directly:

```text
command: restart
target: PC after backend-affecting changes
target: LP after frontend-affecting changes
target: ALL only when both changed
reason: include the compile/test commit SHA
```

Ring should receive an event request first so it can refresh directives. The restart
command already exists; implementation must add `BUILD_MAINTAINER` to the worker and
target enums without changing the semantics of the existing harness `MAINTAINER`.

## Delivery increments

1. Add `BUILD_MAINTAINER` operator-control identity and tests.
2. Add the dedicated worktree launcher and repository lock.
3. Implement compile phase and commit transaction.
4. Implement test phase and commit transaction.
5. Add Ring event request and selective restart signalling.
6. Add systemd user service/timer, dry-run mode and evidence exporter.
7. Run an end-to-end test with intentionally broken compile and test fixtures.

## Acceptance criteria

- No run occurs more frequently than every two hours.
- The agent never performs feature work.
- Compile and test fixes are committed separately and only when their gates are green.
- PC/LP are restarted selectively through the operator-control API.
- Existing uncommitted worker work is never overwritten.
- Every action is attributable through logs, manifest, commit SHA and exported evidence.
