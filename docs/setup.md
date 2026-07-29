# Setup

Prerequisites already installed on the machine:

- Java 21 and Maven;
- Docker with Docker Compose;
- Node/npm and OpenCode;
- Python 3.10+;
- Ollama with the configured models;
- CodeGraph, recommended but not required for compilation/tests.

Run:

```bash
./scripts/setup.sh
```

It creates `.env` from `.env.example` when missing, installs project-local npm/Python
dependencies, initializes CodeGraph when available, starts the persistent PostgreSQL
service, and runs unit tests. It never installs PostgreSQL on the host.
