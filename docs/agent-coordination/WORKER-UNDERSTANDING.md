# Worker understanding assessment

## PC understanding

### Evidence

- `pc-runtime/gate_summary.md` shows green deterministic gate.
- `worker-requests/PC.json` still reports null closure metadata fields.
- `pc-runtime/progress.json` keeps task-07 as `BLOCKED`.

### Assessment

PC implementation appears close to done technically, but the closure packet is incomplete. The immediate issue is not a new backend architecture change; it is completion-quality evidence needed for deterministic closure.

### Required correction (one pass)

- **Level 2 / PC / task-07-populate-production-rag**
- Keep existing scope and produce complete closure metadata + row-count evidence with one exact gate run.

---

## LP understanding

### Evidence

- `lp-runtime/gate_summary.md`: gate failure exit 2.
- `lp-runtime/codex-qwen3-extra-instructions.md`: explicit REVISE packet with banned patterns and required selector mapping.
- `lp-runtime/memory.md`: prior pass timed out; no acceptance claim.

### Assessment

LP currently has an unresolved one-file test-suite correction. Instructions are already precise and bounded; the risk is execution drift (structure damage or selector/state misuse), not ambiguity.

### Required correction (one pass)

- **Level 1 / LP / task-fe-03d-dom-state-tests**
- Apply only the prescribed one-file FE-03D correction packet and run the exact gate sequence.

## Cross-worker clarity for next pass

- Do not widen scope.
- Do not rerun unchanged failing/insufficient approaches.
- Report deterministic evidence that directly satisfies each gate and closure condition.
