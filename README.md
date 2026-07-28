# r4r-spring-ai-rag

Minimal, staged base for a local RAG project and a guided OpenCode/Codex workflow.

## Current scope

Phase 0 only:

- Java 21 and Spring Boot;
- recursive Markdown discovery;
- deterministic heading-aware chunking;
- unit tests;
- a small Python orchestration harness;
- organized OpenCode and Codex instructions;
- PostgreSQL/pgvector infrastructure prepared but not coupled to Java yet.

Not included yet: Angular, REST, custom Ollama clients, embeddings, pgvector persistence, Playwright, CodeGraph, autonomous commits or multi-epoch autopilot.

## First run

```bash
cp .env.example .env
./scripts/install/dev.sh
./scripts/verify.sh
```

Optional database:

```bash
./scripts/db/postgres.sh up
./scripts/db/postgres.sh status
```

OpenCode:

```bash
./scripts/agent/run-opencode.sh
```

Guided cycle (requires a clean Git working tree):

```bash
./scripts/agent/run-cycle.sh
```

The phase order and migration rationale are documented in `docs/ARCHITECTURE.md` and `benchmarks/`.
