# Agent memory

## Current state

- Worker: LP.
- Run: 20260805T191432Z.
- Last accepted task: task-fe-03-rag-ui.
- Active task: task-fe-03b-answer-abstention.
- Current attempt: 2.
- Latest exact gate: task-gate; exit=0.
- Latest Codex decision: pending.
- Checkpoint: pending; head=not recorded.
- Accepted: task-fe-01-angular17-bootstrap, task-fe-02-rag-client, task-fe-03-rag-ui.
- Remaining: task-fe-03b-answer-abstention, task-fe-03c-citations, task-fe-03d-dom-state-tests, task-fe-03e-security-accessibility, task-fe-03f-final-validation, task-fe-04-playwright.
- Exact plan: `.opencode/task-plan.frontend.json`.

## Files currently owned or edited

- `frontend/src/app/features/rag/rag-page.component.html`

## Demonstrated by current evidence

- The exact deterministic task gate completed with exit code 0.
- The checkpoint contains only task-owned product paths plus controller progress/memory.

## Still unproven or below expectations

- Codex has not yet accepted the current checkpoint.

## Approaches not to repeat

- Do not repeat the stopped OpenCode session without changing the plan; stop_reason=session-timeout.

## Next exact action

Preserve a deterministic gate-green checkpoint, generate final evidence and request Codex review.

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
- task-fe-03b-answer-abstention: PENDING — accepted at not accepted; last green attempt=2
- task-fe-03c-citations: PENDING — accepted at not accepted; last green attempt=none
- task-fe-03d-dom-state-tests: PENDING — accepted at not accepted; last green attempt=none
- task-fe-03e-security-accessibility: PENDING — accepted at not accepted; last green attempt=none
- task-fe-03f-final-validation: PENDING — accepted at not accepted; last green attempt=none
- task-fe-04-playwright: PENDING — accepted at not accepted; last green attempt=none
