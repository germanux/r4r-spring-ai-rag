# FE-02 — Typed Angular RAG API client

LP/30B only. Edit `frontend/**` and `docs/frontend/**` only.

Implement typed request, result and citation interfaces plus an injectable service for
`POST /api/rag/answers`. Keep the backend URL environment-driven, preserve citation
order and handle transport errors without inventing answers. Add focused unit tests
using Angular HTTP testing utilities.

Run exactly `./scripts/frontend-task-gate.sh task-fe-02-rag-client`.
