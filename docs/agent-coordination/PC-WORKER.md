# PC code review (backend)

## Current authoritative evidence

- Active backend task is `task-06e-child-process` and still `PENDING`.
  - Evidence: `runtime/ring-agent/ring/20260805T170859Z/pc-runtime/progress.json`
- Deterministic gate summary is green (`exit 0`).
  - Evidence: `runtime/ring-agent/ring/20260805T170859Z/pc-runtime/gate_summary.md`
- Current snapshot has **no Codex review artifact** for PC (`codex_review: null`, `codex_plan: null`).
  - Evidence: `runtime/ring-agent/ring/20260805T170859Z/pc-runtime/manifest.json`
- No task-owned product diff is present in snapshot (`pc-git-diff-stat.txt` empty).
  - Evidence: `runtime/ring-agent/ring/20260805T170859Z/pc-git-diff-stat.txt`

## First current defect

The first blocking defect is **missing Codex closure evidence** for a still-pending task with an already green gate. Without an ACCEPT/REVISE decision tied to current gate evidence, task status cannot advance safely.

## Bounded next action for one worker pass

1. Run a single backend review pass against the existing gate-green `task-06e-child-process` evidence.
2. Obtain and persist Codex decision (`ACCEPT` or `REVISE`) for this exact task snapshot.
3. Only if `REVISE`, execute the minimal correction constrained to the existing packet scope.

## Acceptance conditions / gates

- `./scripts/task-gate.sh task-06e-child-process` returns exit `0`.
- Codex returns `ACCEPT` for `task-06e-child-process` before marking task complete.
- If revisions are required, keep scope bounded to test-side child-process verification packet targets; do not change production scripts/services outside that packet.

## Avoid repeating

- Do not run another unchanged cycle that remains gate-green but still lacks Codex decision evidence.
