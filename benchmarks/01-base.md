# Benchmark 01 — minimal base

Acceptance:

- Java 21.
- Spring context loads.
- Markdown files are discovered recursively in stable order.
- Heading paths are preserved.
- Chunk size is bounded.
- `mvn test` is green.

Excluded: database, embeddings, REST, Angular, Playwright and external LLM calls.
