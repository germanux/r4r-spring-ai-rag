# Codex ↔ Qwen3 extra instructions

- Generated at: 2026-08-05T17:07:42.265993+00:00
- Active task: `task-fe-03b-answer-abstention`
- Codex decision: `REVISE`

## Reviewed or target paths

- `frontend/src/app/features/rag/rag-page.component.spec.ts`
- `frontend/src/app/features/rag/rag-page.component.html`

## Immediate next action

Add focused fixture-based DOM tests for every FE-03B state, make only any minimal template correction those tests expose, then rerun `./scripts/frontend-task-gate.sh task-fe-03b-answer-abstention`.

## Codex assessment of the local understanding

The post-edit report is inadequate: it provides no requirement-to-code/test mapping, acknowledges that no model-authored summary was produced, and incorrectly delegates discovery to review. The local worker recognized the broad state objective but did not recognize that field-only tests violate the explicit DOM-evidence requirement.

## Corrections to ambiguous, inaccurate or misunderstood instructions

- Treat a green shared Angular build/unit-test gate as necessary but insufficient when the task requires particular DOM assertions.
- Do not skip pre-edit understanding solely because an existing generic gate is green; first compare the task-specific acceptance criteria with the tests the gate executes.

## Mandatory resolved instructions for the next local pass

Bounded correction packet: update `rag-page.component.spec.ts` to drive the form through the fixture and call `fixture.detectChanges()` around state transitions. Assert (1) submission displays the loading state, changes the submit button text, disables the submission control, and a second UI submission cannot call the service again; (2) a non-abstained emission renders its answer in the success DOM; (3) an abstained emission renders a nonblank explicit abstention message even if the response answer is blank; (4) an observable error renders exactly `Transport error occurred` in the error DOM; and (5) invoking the rendered clear/reset action removes prior result/error UI, restores idle UI, clears the question, and enables submission controls. Prefer synchronous Subject emissions; remove timeout-based field-only assertions where unnecessary. Make only minimal `rag-page.component.html` changes if a test reveals that the explicit abstention contract is not met. Run `git diff --check` before the exact gate and retain the new full gate log.
