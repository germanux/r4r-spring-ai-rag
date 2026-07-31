# Task 04 — Minimal cited RAG

## Outcome

- Retrieve relevant chunks through the Task 03 store.
- Build a compact deterministic prompt with stable source identifiers.
- Call the configured chat model through Spring AI.
- Return answer plus used source identifiers.
- Abstain when retrieval support is insufficient.
- Unit-test prompt, citation and abstention logic without a live model.
- Keep any live Ollama contract outside the default deterministic gate.

No REST controller, frontend, Playwright or browser workflow in this task.

Gate: `./scripts/task-gate.sh task-04-rag`
