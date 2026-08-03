# R4R Phase 3 foundation — The-Ring ↔ Qwen3 exchange, phase 2.25

## Scope

This increment converts the existing process-only Ring supervisor into a supervisor
for both the PC/LP workers and the cognitive The-Ring loop. It does not claim that
full Phase 3 orchestration is complete.

## Runtime topology

```text
run-ring-system.py
├── ensure-r4r-workers.sh
│   ├── PC wrapper → controller → OpenCode/Qwen3 PC → gates → Codex
│   └── LP wrapper → controller → OpenCode/Qwen3 LP → gates → Codex
└── run-ring-agent.py
    ├── reads bounded PC/LP runtime evidence
    ├── writes one advisory directive per worker
    └── reads the resulting worker evidence in the next cycle
```

## Exchange contract

The-Ring writes:

```text
<RING>/runtime/control/PC/ring-qwen3-directive.json
<RING>/runtime/control/LP/ring-qwen3-directive.json
```

Required fields:

```json
{
  "schema_version": 1,
  "target": "PC",
  "task_id": "task-06-production-ingestion-cli",
  "generated_at": "2026-08-02T21:00:00+00:00",
  "expires_at": "2026-08-03T00:00:00+00:00",
  "priority": "advisory",
  "summary": "Focused cross-stack diagnosis",
  "next_action": "One bounded action",
  "evidence_paths": ["exact/evidence/path"],
  "constraints": ["exact gate and scope constraints"],
  "avoid_repeating": "Previously failed approach"
}
```

The worker controller ignores malformed, stale, wrong-target, wrong-task or
non-advisory directives. Accepted directives are injected into Qwen3 pre-edit, edit
and assimilation prompts and into Codex plan/review context.

## Precedence

1. Exact task specification and allowed paths.
2. Deterministic gate.
3. Current Codex correction packet.
4. The-Ring advisory directive.

The-Ring cannot expand task scope, bypass a gate, authorize Git history writes or
supersede Codex.

## Feedback path

Before each Ring session, Python copies a bounded snapshot of each worker's latest:

- progress file;
- controller state;
- Codex plan and review;
- pre-edit and post-edit local understanding;
- CodeGraph report;
- gate diagnostic summary;
- Codex-to-Qwen3 correction packet;
- previous Ring directive.

This closes the file-based loop:

```text
The-Ring directive → Qwen3/Codex cycle → worker evidence → next The-Ring cycle
```

## Process safety

- The cognitive loop has its own non-blocking file lock.
- The system supervisor records and monitors the cognitive Ring PID.
- Stopping `run-ring-system.sh` terminates the managed cognitive process group.
- `--once` remains guardian-only for deterministic health checks.
- Existing PC and LP dirty product changes are not reset or cleaned.

## Configuration

```text
R4R_RING_REVIEW_INTERVAL_SECONDS=3600
R4R_RING_SESSION_TIMEOUT_SECONDS=5400
R4R_RING_DIRECTIVE_MAX_AGE_SECONDS=10800
R4R_RING_RUN_IMMEDIATELY=true
```

## Activation

After the files are synchronized into the operational code worktree, restart the
supervisor so the new Python process is loaded:

```bash
cd ~/Desarrollo/r4r-integration.git
R4R_RING_WORKTREE=~/Desarrollo/r4r-ring-agent.git \
R4R_PC_WORKTREE=~/Desarrollo/r4r-pc-worker.git \
R4R_LP_WORKTREE=~/Desarrollo/r4r-lp-worker.git \
./scripts/run-ring-system.sh stop

R4R_RING_WORKTREE=~/Desarrollo/r4r-ring-agent.git \
R4R_PC_WORKTREE=~/Desarrollo/r4r-pc-worker.git \
R4R_LP_WORKTREE=~/Desarrollo/r4r-lp-worker.git \
./scripts/run-ring-system.sh start
```
