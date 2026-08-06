# LP code review (frontend)

## Evidence reviewed

- `runtime/ring-agent/ring/20260806T193633Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260806T193633Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260806T193633Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260806T193633Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260806T193633Z/lp-git-diff-stat.txt`
- `runtime/ring-agent/ring/20260806T193633Z/lp-runtime/previous-ring-qwen3-directive.json`

## First current defect

The deterministic FE gate for `task-fe-03d-dom-state-tests` is still red (`exit=2`), and Codex has already issued a concrete `REVISE` packet. The working tree shows a substantial spec-only delta (`rag-page.component.spec.ts`) that is not yet validated by a green rerun in this cycle.

## Bounded next action package

- **Implementation level:** 1 (LP)
- **Assigned role:** LP (laptop-qwen3-worker)
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - `task-fe-03c-citations:ACCEPTED` (shown in LP progress ledger)
  - Existing Codex correction packet for FE-03D
- **allowed_paths:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** mandatory after a green LP gate; no closure without SURGICAL `ACCEPT`.

## Prescribed correction focus (single LP pass)

1. Remove defective synthetic additions called out by Codex.
2. Keep one controlled-pending loading assertion path with no manual loading-flag mutation.
3. Add independent reset tests for success and transport error using fixture-rendered DOM selectors specified in the packet.
4. Preserve valid existing coverage (answer, abstention, citations, transport alert, escaping, service isolation).
5. Produce one consistent evidence set (understanding + gate diagnostics aligned to same run).

## Acceptance conditions

1. Whitespace guard passes (`git diff --check`).
2. Exact FE-03D gate returns exit `0`.
3. Diff remains within LP allowed path.
4. SURGICAL Codex reviews that exact result and returns `ACCEPT` for closure.

## Avoid repeating

- Do not invent test IDs/state values/response shapes.
- Do not mutate `nativeElement.innerHTML` or internal loading flags.
- Do not rerun unchanged failing patches with mismatched diagnostics.
