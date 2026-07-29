# Benchmark 04 — Minimal cited RAG

Build a non-web RAG service using Spring AI abstractions: retrieve relevant chunks, build
a compact prompt, call the configured Ollama chat model, and return an answer with source
identifiers. Add abstention when retrieval support is insufficient. No REST or frontend.
Validate deterministic parts without requiring the model; isolate the live Ollama contract.
