# Agent memory

## Current state

- Last accepted task: None.
- Active task: task-fe-01-angular17-bootstrap.
- Accepted: none.
- Remaining: task-fe-01-angular17-bootstrap, task-fe-02-rag-client, task-fe-03-rag-ui, task-fe-04-playwright.
- Exact plan: `{self.plan_display}`.

## Fixed decisions

- Non-web application until an explicit later task changes scope.
- PostgreSQL only in Docker; Flyway owns application schema.
- Spring AI abstractions; no handwritten Ollama HTTP client.
- Codex plans/reviews read-only; OpenCode edits; Python validates and commits.
- Every red gate produces a full diagnostic log and compressed source bundle for Codex.
- Identical Codex planning evidence is rate-limited; changed failures bypass the cooldown.
- CodeGraph is focused and advisory by default; unavailable MCP evidence does not stop repair.
- Runtime evidence stays under `runtime/runs/`; no automatic push.

## Task commits

- task-fe-01-angular17-bootstrap: PENDING — accepted at not accepted
- task-fe-02-rag-client: PENDING — accepted at not accepted
- task-fe-03-rag-ui: PENDING — accepted at not accepted
- task-fe-04-playwright: PENDING — accepted at not accepted
