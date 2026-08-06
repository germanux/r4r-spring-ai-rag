# Worker Understanding Snapshot

## PC understanding to enforce
- Active task remains `task-07-populate-production-rag`.
- Current state is **not** ready for another coding pass due to dependency sequencing and red gate evidence.
- PC must not widen scope, rerun unchanged loops, or claim progress without a dependency-cleared directive.
- Any closure still requires exact gate green + SURGICAL `ACCEPT`.

## LP understanding to enforce
- Active task remains `task-fe-03d-dom-state-tests`.
- Previous gate green is insufficient because latest Codex decision is `REVISE`.
- LP must perform one bounded revise pass in `frontend/src/app/features/rag/rag-page.component.spec.ts` implementing all mandated missing assertions.
- LP must run `git diff --check` before the exact frontend gate and provide requirement-to-assertion mapping evidence.
- Closure requires SURGICAL `ACCEPT`.

## Shared non-negotiables
- No Git history operations by workers.
- No scope expansion beyond declared `allowed_paths`.
- No bypass of deterministic gates.
