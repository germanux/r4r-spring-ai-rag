# Agent memory

## Current state

- Worker: LP.
- Run: 20260806T164305Z.
- Last accepted task: task-fe-03c-citations.
- Active task: task-fe-03d-dom-state-tests.
- Current attempt: 6.
- Latest exact gate: task-gate; exit=2.
- Latest Codex decision: REVISE.
- Checkpoint: no-product-diff; head=6bd6087d3deec5c01ef1284c508611afdc41de14.
- Accepted: task-fe-01-angular17-bootstrap, task-fe-02-rag-client, task-fe-03-rag-ui, task-fe-03b-answer-abstention, task-fe-03c-citations.
- Remaining: task-fe-03d-dom-state-tests, task-fe-03e-security-accessibility, task-fe-03f-final-validation, task-fe-04-playwright.
- Exact plan: `.opencode/task-plan.frontend.json`.

## Files currently owned or edited

- `frontend/src/app/features/rag/rag-page.component.spec.ts`

## Demonstrated by current evidence

- No new acceptance claim has been demonstrated in this run yet.

## Still unproven or below expectations

- Keep the exclusive write scope to frontend/src/app/features/rag/rag-page.component.spec.ts.
- Delete the newly added synthetic success/abstention test, synthetic innerHTML reset test, malformed loading fragment, manual isLoading mutation, and all unnecessary of/tick usage.
- Restore one controlled-pending loading test using querySubject: submit once, detect changes, assert .loading-state[role="status"] contains "Processing your question...", assert the rendered textarea and .submit-button are disabled, call component.onSubmit() once more while pending, and assert exactly one total service call.
- Create an independent success-reset test: submit, emit successResponse with citations, detect changes, assert .answer-content and .citations-section exist, call clear(), detect changes, then assert .answer-content, .citations-section, and .error-state are absent and .idle-state exists.
- Create an independent transport-error reset test with a fresh Subject: submit, emit an error, detect changes, assert .error-state[role="alert"] exists, call clear(), detect changes, then assert the alert is absent and .idle-state exists.
- Preserve existing valid answer, abstention, citation, transport-alert, escaping, and service-isolation coverage. Use valid RAGAnswerResult values, fixture-rendered DOM, two-space indentation, balanced braces, and no trailing whitespace.
- The next local understanding report must map every FE-03D requirement to its exact DOM selector and assertion. task-gate.json, gate-full.log, and the diagnostic manifest must describe the same final execution.

## Approaches not to repeat

- Keep the exclusive write scope to frontend/src/app/features/rag/rag-page.component.spec.ts.
- Delete the newly added synthetic success/abstention test, synthetic innerHTML reset test, malformed loading fragment, manual isLoading mutation, and all unnecessary of/tick usage.
- Restore one controlled-pending loading test using querySubject: submit once, detect changes, assert .loading-state[role="status"] contains "Processing your question...", assert the rendered textarea and .submit-button are disabled, call component.onSubmit() once more while pending, and assert exactly one total service call.
- Create an independent success-reset test: submit, emit successResponse with citations, detect changes, assert .answer-content and .citations-section exist, call clear(), detect changes, then assert .answer-content, .citations-section, and .error-state are absent and .idle-state exists.
- Create an independent transport-error reset test with a fresh Subject: submit, emit an error, detect changes, assert .error-state[role="alert"] exists, call clear(), detect changes, then assert the alert is absent and .idle-state exists.
- Preserve existing valid answer, abstention, citation, transport-alert, escaping, and service-isolation coverage. Use valid RAGAnswerResult values, fixture-rendered DOM, two-space indentation, balanced braces, and no trailing whitespace.
- The next local understanding report must map every FE-03D requirement to its exact DOM selector and assertion. task-gate.json, gate-full.log, and the diagnostic manifest must describe the same final execution.

## Next exact action

Replace the defective additions with the prescribed controlled-pending loading test and two independent fixture-rendered reset tests, then run git diff --check and the exact FE-03D gate and submit consistent fresh evidence.

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
- task-fe-03d-dom-state-tests: PENDING — accepted at not accepted; last green attempt=4
- task-fe-03e-security-accessibility: PENDING — accepted at not accepted; last green attempt=none
- task-fe-03f-final-validation: PENDING — accepted at not accepted; last green attempt=none
- task-fe-04-playwright: PENDING — accepted at not accepted; last green attempt=none
