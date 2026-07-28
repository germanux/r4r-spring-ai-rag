# Setup

## Required for Phase 0

- Java 21;
- Maven;
- Python 3.10 or newer;
- Node/npm only for the OpenCode plugin.

Run:

```bash
./scripts/install/dev.sh
./scripts/verify.sh
```

## PostgreSQL/pgvector

Docker Compose is the default because it is isolated and reproducible:

```bash
./scripts/db/postgres.sh up
```

For a system installation on Ubuntu/Zorin, use the explicit optional script:

```bash
./scripts/install/postgres-ubuntu.sh
```

That script asks before using `sudo` and aborts when no pgvector package is available.

## Ollama/OpenCode model alias

The default OpenCode configuration expects an Ollama model named `r4r-coder`.
Create that alias from the model already validated on the target machine, or edit
`opencode.jsonc` and `.opencode/agents/r4r-local.md` together.
