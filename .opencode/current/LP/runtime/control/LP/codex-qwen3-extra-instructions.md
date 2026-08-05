# Codex ↔ Qwen3 extra instructions

- Generated at: 2026-08-05T20:18:40.498324+00:00
- Active task: `task-fe-03c-citations`
- Codex decision: `REVISE`

## Reviewed or target paths

- `frontend/src/app/features/rag/rag-page.component.spec.ts`
- `frontend/src/app/features/rag/rag-page.component.html`
- `frontend/src/app/features/rag/rag-page.component.ts`

## Immediate next action

Add focused rendered-DOM tests for every missing FE-03C requirement, then rerun `./scripts/frontend-task-gate.sh task-fe-03c-citations`.

## Codex assessment of the local understanding

The local understanding is inadequate: the pre-edit report was skipped, the post-edit report incorrectly maps the requirement only to `.opencode/memory.frontend.md`, and neither report evaluates the actual citation component or tests. No local-model implementation change occurred because the behavior already existed, but the model failed to recognize that the current tests do not prove the full acceptance contract.

## Corrections to ambiguous, inaccurate or misunderstood instructions

- Treat a green generic Angular build/test run as necessary but insufficient; inspect whether task-specific assertions cover every required behavior.
- For FE-03C, require DOM assertions for ordinal order, source, complete ordered heading path, omission on an empty citation array in a non-abstained success response, and non-parsing of citation-like answer text.

## Mandatory resolved instructions for the next local pass

Modify only `frontend/src/app/features/rag/rag-page.component.spec.ts` unless a failing focused test exposes a real component defect. Add bounded DOM tests that: (1) submit a successful response whose citations arrive out of order and assert rendered item order plus each displayed ordinal, source, and multi-segment heading path in the original segment order; (2) submit `{ answer: '...', abstained: false, citations: [] }` and assert `.citations-section` is absent; and (3) submit an answer containing citation-like model text such as `[1] Fake Source > Fake Heading` while structured `citations` is empty, assert that text remains only in `.answer-content`, and assert no `.citation-item` or `.citations-section` is created. Keep assertions against rendered DOM rather than component getters. Run `git diff --check` and the exact gate `./scripts/frontend-task-gate.sh task-fe-03c-citations`; provide an accurate pre/post understanding mapping and the complete gate evidence.
