# Task 04 — Minimal cited RAG

## Objective

Build a minimal non-web RAG service using Spring AI abstractions.

## Required outcome

- Retrieve relevant chunks from the vector store.
- Build a compact prompt containing stable source identifiers.
- Call the configured Ollama chat model through Spring AI.
- Return an answer and the source identifiers used.
- Abstain when retrieval support is insufficient.
- Keep deterministic prompt, citation and abstention logic unit-testable without a
  live model.
- Add focused test `CitedRagServiceTest`.

## Restrictions

No REST controller, Angular, Playwright or autonomous browser workflow. Isolate any
live Ollama contract from the default deterministic test gate.

## Gate

`./scripts/task-gate.sh task-04-rag`
