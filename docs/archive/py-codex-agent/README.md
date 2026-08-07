# R4R Codex/OpenCode controller

The controller runs one locked task at a time:

1. exact task gate with disposable PostgreSQL when required;
2. deterministic diagnostic classification;
3. untruncated gate log plus compressed implicated-file bundle;
4. focused advisory CodeGraph map;
5. read-only local pre-edit understanding;
6. read-only Codex plan;
7. bounded OpenCode edit;
8. exact task gate and local assimilation;
9. controlled progress/commit on green; optional Codex review only when explicitly
   re-enabled.

Identical diagnostic fingerprints reuse the latest Codex plan during
`R4R_CODEX_MIN_INTERVAL_SECONDS` (default 3600). New evidence bypasses the cooldown.
CodeGraph defaults to `R4R_CODEGRAPH_POLICY=advisory` so MCP outages do not mask Maven
or source evidence.

Run from the repository root:

```bash
./scripts/run-codex-agent.sh
./scripts/run-codex-agent.sh --status
```

For a manual Maven lifecycle that needs the disposable integration database:

```bash
./scripts/mvn-with-test-db.sh install
```

## Resume model

Active-task lock files are disabled. The controller resumes from
`.opencode/progress.json` and accepts task-scoped dirty work plus maintenance paths.
A stale `runtime/locks/active-task.json` is deleted on startup.

Progress, memory and `.opencode/current/` are machine-local continuity state. They are
ignored and never included in a checkpoint or closing commit. Durable cross-agent
evidence is written separately under `.ring-agent/evidence/<task-id>/`.

## Local file-change sound

When the local LLM changes at least one non-runtime, task-owned repository file during
an edit attempt, the controller invokes:

```bash
./scripts/notify-success.sh --file-changed "<task>: local LLM changed repository files"
```

The default success sound path is:

```text
scripts/u-freesound_community-success-1-6297.mp3
```

Set `R4R_SUCCESS_SOUND=/absolute/path/to/another.mp3` to override it. If the file or an
MP3 player is unavailable, the notifier falls back to the terminal bell and the worker
continues.

## Compact LP task report

The compact laptop worker now returns a `# Local understanding report` in the same
model response that contains its replacement files. This avoids a second implementation
session while still giving Codex a model-authored explanation of the task, instructions,
changed files and unresolved points. After the exact frontend gate runs, the Python
controller appends the authoritative gate exit code and timeout state before sending the
report to Codex. The report is stored per attempt at:

```text
runtime/runs/LP/<RUN-ID>/<TASK-ID>/attempt-XX/evidence/local-understanding.md
```

## Notification policy

`R4R_NOTIFICATION_MODE` controls audible notifications:

- `changes` (default): play only the success MP3 after a real local-LLM file change;
- `important`: file-change and rate-limited error MP3s;
- `errors`: only rate-limited error MP3s;
- `all`: legacy behavior, including gate and Codex-handoff terminal bells;
- `off`: log notification events without producing sound.

Error sounds are rate-limited by `R4R_ERROR_SOUND_COOLDOWN_SECONDS` (default 900).
The current effective policy can be inspected with:

```bash
./scripts/notify-success.sh --status
```

## The-Ring advisory directive

When `R4R_RING_WORKTREE` is set, the controller reads the current worker directive
from `runtime/control/<PC|LP>/ring-qwen3-directive.json` in the Ring worktree. A
directive is used only for the matching active task, must be recent and must declare
`priority: advisory`. It is included in Qwen3 and Codex contexts but cannot override
the task specification, exact gate or current Codex correction packet.

## Gate-green checkpoints and session watchdog

The deterministic controller can create a task-scoped checkpoint immediately after the
exact gate turns green (`R4R_CHECKPOINT_ON_GREEN=true`). With the current canonical
`requireSurgicalReview: false`, it then closes and advances the task without waiting
for `ACCEPT/REVISE`. Set `R4R_REQUIRE_SURGICAL_REVIEW=true` only when the redesigned
review lane is deliberately re-enabled. Model sessions never execute Git writes.

OpenCode JSONL sessions are bounded by wall time, useful inactivity, step count and
repeated-event budgets. `step_start` alone is not progress. Every watchdog stop is
written to the attempt evidence with its exact `stop_reason`.
