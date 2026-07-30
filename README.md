# R4R Spring AI RAG

Small non-web Java 21 project for building a local RAG system with deterministic
validation and an automated Codex/OpenCode task cycle.

## Main components

- Spring Boot, Spring AI and Ollama;
- JDBC, Flyway and PostgreSQL with pgvector;
- deterministic Markdown loading and bounded heading-aware chunking;
- persistent development database and disposable integration-test database;
- OpenCode with CodeGraph available through MCP;
- automatic Codex planning/review with OpenCode implementation;
- generated logs, evidence, decisions and task state under `runtime/`.

## Setup

```bash
./scripts/setup.sh
./scripts/verify.sh all
```

On Debian, Ubuntu and Zorin OS, `setup.sh` installs missing host prerequisites
with `sudo`, including Docker, Docker Compose, Java 21, Maven, Python, Node/npm,
OpenCode, Codex CLI and CodeGraph.

PostgreSQL is **not** installed on the host. Both databases run in Docker.

If the setup script adds your user to the `docker` group, log out and back in
before the next execution to avoid the temporary `sudo docker` fallback.

## Environment configuration

The project uses a local `.env` file for ports, credentials, endpoints, model
names and agent settings.

- Docker Compose reads it through `scripts/db.sh --env-file`.
- Spring Boot imports it from `application.yml`.
- Shell launchers source it only for themselves and their child processes.
- It does not permanently modify system-level or user-level environment variables.
- `.env` is ignored by Git; `.env.example` is the versioned template.

See [`docs/environment.md`](docs/environment.md) for the exact loading rules.

## PostgreSQL operations

```bash
./scripts/db.sh up
./scripts/db.sh down
./scripts/db.sh status
./scripts/db.sh logs
./scripts/db.sh reset --yes

./scripts/db.sh test-up
./scripts/db.sh test-down
./scripts/db.sh test-logs
```

The development database is persistent under:

```text
docker-postgres/data/app/
```

The integration-test database uses disposable storage and is recreated for clean
test executions. PostgreSQL runtime data and local backups are ignored by Git.

## Verification

```bash
./scripts/verify.sh unit
./scripts/verify.sh integration
./scripts/verify.sh all
```

The full verification flow starts the test database, runs unit and integration
tests, applies Flyway migrations and validates the PostgreSQL/pgvector baseline.

## Automatic task cycle

```bash
./scripts/run-codex-agent.sh
```

With the default configuration, no manual task selection or intermediate commit
is required. The controller:

1. verifies already accepted tasks;
2. selects the first pending or regressed task;
3. asks Codex for a structured read-only plan;
4. runs OpenCode with the selected task and plan;
5. executes the task-specific deterministic gate;
6. asks Codex to return `ACCEPT`, `REVISE` or `BLOCKED`;
7. permits a bounded number of revisions;
8. updates progress and concise memory;
9. creates an accepted local commit when enabled;
10. advances automatically until all tasks are complete or a real blocker occurs.

The controller never pushes to a remote repository.

Inspect current progress without starting a new cycle:

```bash
./scripts/run-codex-agent.sh --status
```

## Task structure

- Parent task: `.opencode/commands/task.md`
- Ordered subtasks:
  - `.opencode/commands/task-01-base.md`
  - `.opencode/commands/task-02-ingestion.md`
  - `.opencode/commands/task-03-pgvector.md`
  - `.opencode/commands/task-04-rag.md`
- Machine-readable plan: `.opencode/task-plan.json`
- Progress state: `.opencode/progress.json`
- Concise working memory: `.opencode/memory.md`

## Direct agent operations

Run OpenCode directly:

```bash
./scripts/run-opencode.sh
```

Run the complete Codex/OpenCode controller:

```bash
./scripts/run-codex-agent.sh
```

## Runtime output

All generated execution data is stored under:

```text
runtime/
├── runs/
└── locks/
```

This includes logs, evidence, Codex decisions, token usage, task state and recovery
information. Runtime output is ignored by Git.

## License

Copyright (c) 2026 Germán Caballero Rodríguez.

This project is proprietary software. See [`LICENSE`](LICENSE).
