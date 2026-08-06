# Worker understanding assessment

## PC understanding quality

- Evidence indicates process awareness of gate-green status, but the pass did not produce closure metadata (`codex_decision` and `checkpoint_head` remain null).
- Defect type: **workflow/closure understanding gap**, not necessarily code gap.

### Required next understanding output (PC)

- **Level 3 / SURGICAL reviewer** must classify one of:
  1. Gate-green plus acceptable diff => `ACCEPT` path with controller closure evidence.
  2. Gate-green but non-acceptable diff => `REVISE` packet with first concrete defect and bounded write scope.

## LP understanding quality

- `local_understanding.md` is explicitly inadequate per Codex extra instructions.
- Defect type: **requirement-to-assertion mapping gap** and prohibited-pattern regression in a single spec file.

### Required next understanding output (LP)

For `task-fe-03d-dom-state-tests`, LP must include a concise mapping from requirement to selector/assertion:

1. Loading status → `.loading-state[role="status"]`
2. Disabled controls → `textarea` and `.submit-button`
3. Transport failure → `.error-state[role="alert"]`
4. Answer visibility → `.answer-content`
5. Reset cleanup → absence of answer/error/citations + presence of `.idle-state`

## Bounded actions with closure contract

- **PC path:** Level 3 SURGICAL review-only classification on `task-07-populate-production-rag`; required closure contract is hierarchy policy (`exact-gate-green + scope-clean + surgical-accept + controller-commit`).
- **LP path:** Level 1 LP single-file correction on `task-fe-03d-dom-state-tests`; exact gate must pass, then SURGICAL must review and `ACCEPT` before closure.
