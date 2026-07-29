# R4R Spring AI RAG

Small non-web Java 21 baseline for a local RAG system:

- Spring Boot, Spring AI and Ollama;
- JDBC, Flyway and PostgreSQL/pgvector;
- deterministic Markdown loading and chunking;
- persistent development DB plus disposable integration-test DB;
- OpenCode with CodeGraph available;
- bounded optional Codex review controller;
- all generated logs/evidence under `runtime/`.

## Start

```bash
./scripts/setup.sh
./scripts/verify.sh all
```

`setup.sh` does not install PostgreSQL or use `sudo`; Docker runs PostgreSQL.
Edit `.env` for local endpoints, ports, credentials or model names. See
`docs/environment.md` for the exact loading rules.

## Main operations

```bash
./scripts/db.sh up
./scripts/db.sh down
./scripts/db.sh status
./scripts/db.sh logs
./scripts/db.sh reset --yes

./scripts/db.sh test-up
./scripts/db.sh test-down
```

The initial active task is `.opencode/commands/benchmark-01-base.md`. Commit the imported baseline manually before running `./scripts/run-codex-agent.sh`, because the bounded controller requires a clean working tree.

## Agents

./scripts/run-opencode.sh
./scripts/run-codex-agent.sh
