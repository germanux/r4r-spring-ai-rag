# Agent memory

## Current state

- Worker: LP.
- Run: 20260806T145913Z.
- Last accepted task: task-fe-03c-citations.
- Active task: task-fe-03d-dom-state-tests.
- Current attempt: 6.
- Latest exact gate: task-gate; exit=0.
- Latest Codex decision: REVISE.
- Checkpoint: created; head=a8db23a480bbc8735eba64226e83ccb67013c4b2.
- Accepted: task-fe-01-angular17-bootstrap, task-fe-02-rag-client, task-fe-03-rag-ui, task-fe-03b-answer-abstention, task-fe-03c-citations.
- Remaining: task-fe-03d-dom-state-tests, task-fe-03e-security-accessibility, task-fe-03f-final-validation, task-fe-04-playwright.
- Exact plan: `.opencode/task-plan.frontend.json`.

## Files currently owned or edited

- `frontend/src/app/features/rag/rag-page.component.spec.ts`

## Demonstrated by current evidence

- The exact deterministic task gate completed with exit code 0.
- The checkpoint contains only task-owned product paths plus controller progress/memory.

## Still unproven or below expectations

- Keep the write scope exclusively on frontend/src/app/features/rag/rag-page.component.spec.ts.
- Normalize the loading test to standard two-space describe-block indentation and indent all statements consistently.
- After the initial pending submission and fixture.detectChanges(), query .loading-state[role="status"], textarea, and .submit-button. Assert loading text is visible and both rendered controls have disabled === true; retain the FormControl assertion only as supplementary evidence.
- Call component.onSubmit() once more while the request remains pending, then assert ragApiService.query has exactly one total call.
- Split reset coverage into two tests. In the success-reset test, render successResponse with citations, assert .answer-content and .citations-section exist before clear(), then assert .answer-content, .citations-section, and .error-state are absent and .idle-state exists afterward.
- In a separate transport-error-reset test, render a transport error, assert .error-state[role="alert"] exists before clear(), then assert it is absent and .idle-state exists afterward.
- Preserve the existing answer, abstention, citation, transport-alert, and service tests; do not modify production code or introduce live backend or LLM dependencies.
- Run git diff --check before ./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests. Retain a complete, internally consistent exit-0 evidence set and provide an accurate requirement-to-assertion mapping.

## Approaches not to repeat

- Keep the write scope exclusively on frontend/src/app/features/rag/rag-page.component.spec.ts.
- Normalize the loading test to standard two-space describe-block indentation and indent all statements consistently.
- After the initial pending submission and fixture.detectChanges(), query .loading-state[role="status"], textarea, and .submit-button. Assert loading text is visible and both rendered controls have disabled === true; retain the FormControl assertion only as supplementary evidence.
- Call component.onSubmit() once more while the request remains pending, then assert ragApiService.query has exactly one total call.
- Split reset coverage into two tests. In the success-reset test, render successResponse with citations, assert .answer-content and .citations-section exist before clear(), then assert .answer-content, .citations-section, and .error-state are absent and .idle-state exists afterward.
- In a separate transport-error-reset test, render a transport error, assert .error-state[role="alert"] exists before clear(), then assert it is absent and .idle-state exists afterward.
- Preserve the existing answer, abstention, citation, transport-alert, and service tests; do not modify production code or introduce live backend or LLM dependencies.
- Run git diff --check before ./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests. Retain a complete, internally consistent exit-0 evidence set and provide an accurate requirement-to-assertion mapping.

## Next exact action

Revise only frontend/src/app/features/rag/rag-page.component.spec.ts to complete the missing loading and reset DOM assertions, then run git diff --check and the exact task gate.

## Fixed decisions

- OpenCode/Qwen3 and Codex never write Git history.
- The deterministic Python controller may create a gate-green checkpoint and a final ACCEPT commit.
- A gate-green checkpoint preserves useful work but does not mark the task ACCEPTED.
- A task completes only after its exact gate is green and Codex returns `ACCEPT`.
- PostgreSQL only in Docker; Flyway owns application schema.
- Spring AI abstractions; no handwritten Ollama HTTP client.
- Every red gate retains complete diagnostics for Codex.
- CodeGraph is focused retrieval evidence, not authority to expand task scope.
- Markdown/JSON activity is published under `.opencode/current/{ring,PC,LP}/`; raw runtime is never committed directly.

## Task ledger

- task-fe-01-angular17-bootstrap: ACCEPTED — accepted at 2026-08-05T17:04:46.121118+00:00; last green attempt=1
- task-fe-02-rag-client: ACCEPTED — accepted at 2026-08-05T17:05:49.579162+00:00; last green attempt=1
- task-fe-03-rag-ui: ACCEPTED — accepted at 2026-08-05T17:06:42.020494+00:00; last green attempt=1
- task-fe-03b-answer-abstention: ACCEPTED — accepted at 2026-08-05T20:17:20.426349+00:00; last green attempt=2
- task-fe-03c-citations: ACCEPTED — accepted at 2026-08-06T15:04:37.257786+00:00; last green attempt=1
- task-fe-03d-dom-state-tests: PENDING — accepted at not accepted; last green attempt=6
- task-fe-03e-security-accessibility: PENDING — accepted at not accepted; last green attempt=none
- task-fe-03f-final-validation: PENDING — accepted at not accepted; last green attempt=none
- task-fe-04-playwright: PENDING — accepted at not accepted; last green attempt=none
