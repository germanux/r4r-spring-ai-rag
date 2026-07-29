# R4R Spring AI RAG

Small non-web Java 21 RAG project with:

- Spring Boot, Spring AI and the existing Ollama coding/model configuration;
- JDBC, Flyway and PostgreSQL/pgvector;
- deterministic Markdown loading and bounded heading-aware chunking;
- persistent development DB plus disposable integration-test DB;
- OpenCode with CodeGraph available;
- automatic Codex planning/review and OpenCode implementation;
- unified generated logs and evidence under `runtime/`.

## Replace an earlier project copy

Preserve `.git`, delete the previous working files, then extract this ZIP at the
repository root:

```bash
cd ~/Desarrollo/r4r-spring-ai-rag.git
find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
unzip ~/Descargas/r4r-spring-ai-rag-complete-v0.3.0.zip -d .
chmod +x scripts/*.sh
```

## Setup and validation

```bash
./scripts/setup.sh
./scripts/verify.sh all
```

`setup.sh` installs missing host prerequisites with `sudo` on Debian/Ubuntu/Zorin,
including Docker, Docker Compose, Java 21, Maven, Python, Node/npm, OpenCode,
Codex CLI and CodeGraph. It does **not** install PostgreSQL on the host.

## Automatic agent cycle

```bash
./scripts/run-codex-agent.sh
```

No manual task selection or commit is required with the default `.env` values. The
controller:

1. commits the imported green baseline when the repository is initially dirty;
2. selects the first pending or regressed task;
3. asks Codex for a structured read-only plan;
4. runs OpenCode against the existing Ollama model;
5. executes the task-specific deterministic gate;
6. asks Codex to accept, revise or block;
7. creates an accepted local commit;
8. advances automatically to the next task.

It never pushes. Use `./scripts/run-codex-agent.sh --status` to inspect task and gate
status. An interrupted failed task can resume only when its dirty paths match the
saved task lock.

## Tasks

- Parent: `.opencode/commands/task.md`
- Subtasks: `.opencode/commands/task-01-base.md` through `task-04-rag.md`
- Machine plan: `.opencode/task-plan.json`
- Progress: `.opencode/progress.json`
- Concise state: `.opencode/memory.md`

## Environment loading

`.env` is project-local and ignored by Git. It is loaded by:

- Docker Compose through `scripts/db.sh --env-file`;
- Spring Boot through `spring.config.import` in `application.yml`;
- launcher scripts through `source .env`, affecting only those scripts and child
  processes.

`.env.example` is the versioned template. See `docs/environment.md`.
