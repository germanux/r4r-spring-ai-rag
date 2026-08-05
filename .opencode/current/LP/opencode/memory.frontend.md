# Agent memory

## Current state

- Worker: LP.
- Run: 20260805T191432Z.
- Last accepted task: task-fe-03b-answer-abstention.
- Active task: task-fe-03c-citations.
- Current attempt: 2.
- Latest exact gate: task-gate; exit=0.
- Latest Codex decision: REVISE.
- Checkpoint: no-product-diff; head=not recorded.
- Accepted: task-fe-01-angular17-bootstrap, task-fe-02-rag-client, task-fe-03-rag-ui, task-fe-03b-answer-abstention.
- Remaining: task-fe-03c-citations, task-fe-03d-dom-state-tests, task-fe-03e-security-accessibility, task-fe-03f-final-validation, task-fe-04-playwright.
- Exact plan: `.opencode/task-plan.frontend.json`.

## Files currently owned or edited

- No task-owned dirty product path at the latest snapshot.

## Demonstrated by current evidence

- The exact deterministic task gate completed with exit code 0.
- The checkpoint contains only task-owned product paths plus controller progress/memory.

## Still unproven or below expectations

- Treat a green generic Angular build/test run as necessary but insufficient; inspect whether task-specific assertions cover every required behavior.
- For FE-03C, require DOM assertions for ordinal order, source, complete ordered heading path, omission on an empty citation array in a non-abstained success response, and non-parsing of citation-like answer text.

## Approaches not to repeat

- Treat a green generic Angular build/test run as necessary but insufficient; inspect whether task-specific assertions cover every required behavior.
- For FE-03C, require DOM assertions for ordinal order, source, complete ordered heading path, omission on an empty citation array in a non-abstained success response, and non-parsing of citation-like answer text.

## Next exact action

Add focused rendered-DOM tests for every missing FE-03C requirement, then rerun `./scripts/frontend-task-gate.sh task-fe-03c-citations`.

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
- task-fe-03c-citations: PENDING — accepted at not accepted; last green attempt=1
- task-fe-03d-dom-state-tests: PENDING — accepted at not accepted; last green attempt=none
- task-fe-03e-security-accessibility: PENDING — accepted at not accepted; last green attempt=none
- task-fe-03f-final-validation: PENDING — accepted at not accepted; last green attempt=none
- task-fe-04-playwright: PENDING — accepted at not accepted; last green attempt=none
