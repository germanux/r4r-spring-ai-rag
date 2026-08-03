# R4R Phase 3 operational control v1

## Scope

This increment advances the Phase 3 foundation. It does **not** claim that full
multi-agent orchestration is complete. It closes three operational gaps observed in
long-running PC and LP sessions:

1. preserve a useful task-scoped Git checkpoint immediately after the exact gate is
   green, without waiting for final Codex acceptance;
2. stop OpenCode sessions that exceed wall-time, useful-activity, step or repeated
   event budgets;
3. give The-Ring event-driven worker memory and checkpoint evidence instead of waiting
   only for the hourly review.

## Authority and Git history

OpenCode/Qwen3 and Codex remain read-only with respect to Git history. The deterministic
Python controller is the only component allowed to create automatic commits.

A green checkpoint uses a message such as:

```text
wip(pc/task-06-production-ingestion-cli): gate-green checkpoint attempt-03
```

It contains only the current task's owned product paths plus its progress and memory
files. The task remains `PENDING`. After Codex returns `ACCEPT`, the controller records
acceptance and creates the normal closing commit.

## Worker memory contract

Each worker maintains its own memory:

```text
.opencode/memory.backend.md
.opencode/memory.frontend.md
```

The memory records:

- active task, run and attempt;
- exact gate result and latest Codex decision;
- task-owned files currently edited;
- claims demonstrated by current evidence;
- acceptance conditions still unproven;
- approaches that must not be repeated;
- one exact next action;
- latest checkpoint status.

Codex already receives this memory in every structured plan/review prompt.

## Bounded OpenCode sessions

The controller inspects OpenCode JSONL output. A periodic `step_start` is not useful
progress. A session is terminated with exit `124` and a recorded `stop_reason` when it
exceeds any configured budget:

```text
session-timeout
idle-timeout
step-limit
repeat-event-budget
```

Defaults are resolved from `config/r4r-agents.json`; environment variables may
override them. PC and LP have independent limits.

## Event-driven The-Ring feedback

After a green checkpoint and after each Codex decision, the controller writes an
atomic request under:

```text
runtime/control/RING/requests/PC.json
runtime/control/RING/requests/LP.json
```

The cognitive Ring loop notices pending requests and starts a fresh session after the
minimum event interval. It snapshots worker progress, memory, latest checkpoint,
Codex evidence, gate evidence and the request. Requests are consumed exactly once and
preserved in the Ring run directory.

The normal hourly review remains as a fallback. Ring directives stay advisory and may
not override task scope, the deterministic gate or Codex.

## Important configuration

```text
R4R_AUTO_COMMIT=true
R4R_CHECKPOINT_ON_GREEN=true
R4R_MAX_ATTEMPTS_PER_TASK=6
R4R_MAX_NO_PROGRESS_CYCLES=2
R4R_MAX_TRANSIENT_FAILURES=3

R4R_PC_MAX_SESSION_SECONDS=5400
R4R_PC_IDLE_SECONDS=900
R4R_PC_MAX_SESSION_STEPS=120
R4R_PC_REPEAT_EVENT_BUDGET=12

R4R_LP_MAX_SESSION_SECONDS=3600
R4R_LP_IDLE_SECONDS=600
R4R_LP_MAX_SESSION_STEPS=90
R4R_LP_REPEAT_EVENT_BUDGET=12

R4R_RING_REVIEW_INTERVAL_SECONDS=3600
R4R_RING_EVENT_MIN_INTERVAL_SECONDS=300
```

## Activation safety

Source files may be synchronized while old workers are running because already-loaded
Python processes keep their current code. The new behavior becomes active only after a
controlled supervisor stop, branch synchronization and restart. Dirty PC/LP product
work must never be reset or cleaned during activation.
