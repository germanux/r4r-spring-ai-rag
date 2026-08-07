# LP code review (Ring)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T020031Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T020031Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T020031Z/lp-runtime/memory.md`
- `runtime/ring-agent/ring/20260807T020031Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T020031Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T020031Z/lp-git-diff-stat.txt`

## First current defect

`task-fe-03d-dom-state-tests` remains pending with unresolved correction evidence:

- Deterministic gate summary reports failure (`exit code: 2`).
- Active Codex packet is `REVISE` with explicit one-file corrections.
- Current run shows no new gate-green evidence.
- Prior LP attempt also hit session timeout (`stop_reason=session-timeout` in memory).

## Bounded next work package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations: ACCEPTED` (already satisfied)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Objective:** Complete the prescribed one-file FE-03D spec correction and re-prove the task gate.

### One-pass action

Apply exactly the correction packet in `codex-qwen3-extra-instructions.md`:

1. Restore valid suite structure and remove defective attempt additions.
2. Keep one controlled-pending loading/duplicate-submit DOM test.
3. Add independent success-reset and transport-error-reset tests.
4. Preserve existing valid answer/abstention/citation/escaping/service-isolation coverage.
5. Run `git diff --check` then the exact FE-03D gate once.

### Exact gate

```bash
./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests
```

### Acceptance conditions

- Diff is limited to the single allowed file.
- `git diff --check` is clean.
- FE-03D gate exits 0 on this pass.
- Evidence is internally consistent (patch, diagnostics, and understanding reflect the same final run).

### Avoid repeating

Do not reintroduce malformed braces/indentation, internal-state mutations, `innerHTML` mutation, guessed selectors, or timeout-only reruns without plan change.
