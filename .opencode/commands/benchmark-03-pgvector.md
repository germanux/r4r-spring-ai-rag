# Benchmark 03 — Spring AI PgVectorStore

Use Spring AI's PgVector integration with the existing datasource and Ollama embedding
model. Flyway remains schema authority. Persist and retrieve the sample corpus with 768
embedding dimensions and cosine similarity. Do not introduce a handwritten Ollama client.
Prove behavior with focused integration tests and `./scripts/verify.sh all`.
