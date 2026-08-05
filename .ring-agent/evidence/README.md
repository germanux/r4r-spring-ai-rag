# Ring task evidence

Ring writes one durable Markdown summary for each task, agent/model and attempt:

```text
.ring-agent/evidence/<task-id>/<assigned-agent>-attempt-NN.md
```

Each file has one authoritative writer. The task's `allowed_paths` value is recorded
as its canonical `write_scope`; Ring refuses to publish concurrent active directives
when scopes overlap. Every directive records `assigned_agent`, `model`, `branch`,
`write_scope` and its exclusive `evidence_path`. Detailed logs, locks and process state
remain under the ignored `runtime/` directory.
