# Task 05 — Stable cited RAG HTTP API

## Ownership

PC/80B backend worker only. Do not modify `frontend/**`.

## Outcome

Expose the existing `CitedRagService` through a minimal JSON endpoint suitable for the
Angular 17 client. Use Spring Web, typed request/response records, validation and
focused MVC tests. Preserve the service's abstention and citation metadata exactly.

## Contract

- `POST /api/rag/query`
- request: `{ "question": "..." }`
- response contains `answer`, `abstention` and ordered `citations`;
- each citation contains `label`, `source`, `headingPath` and `ordinal`;
- invalid questions return a deterministic 4xx response;
- no handwritten Ollama HTTP client;
- no frontend files.

## Gate

Run exactly `./scripts/task-gate.sh task-05-rag-api`.
