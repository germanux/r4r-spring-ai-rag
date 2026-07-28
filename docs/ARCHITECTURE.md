# Architecture and migration audit

## Audit of the previous repository

The inspected snapshot contained 273 files: 72 Markdown, 44 Java, 24 Python,
19 shell scripts, 16 logs and 14 tracked Python bytecode files. The code volume
was approximately 9,388 Python lines versus 2,584 Java lines. Runtime evidence,
agent state, source code, generated frontend output and installation assets lived
inside the same tree.

Main problems:

1. Product code and orchestration infrastructure had comparable or inverted weight.
2. Three control layers overlapped: OpenCode, large Python supervisors and Codex review.
3. Machine decisions crossed Markdown files with loose text contracts.
4. Java scope already included custom Ollama clients, pgvector, REST, Angular and E2E.
5. Logs, locks, bytecode and run bundles polluted the repository.
6. The system optimized control of failures more than delivery of product increments.

## New boundaries

- `src/`: the product. It starts with loading and chunking only.
- `benchmarks/`: ordered acceptance contracts.
- `.opencode/`: OpenCode-specific configuration and prompts.
- `agent/codex/`: Codex review contract and JSON schema.
- `agent/shared/`: one active JSON task and one concise human memory file.
- `tools/orchestrator/`: deterministic runner with no Git writes.
- `scripts/`: thin entry points grouped by purpose.
- `infra/`: optional external services.
- `runtime/`: ignored logs and evidence.

## Explicit removals from the initial base

- Angular and Playwright;
- REST controllers and DTOs;
- custom `ChatClient`/`EmbeddingClient` Ollama implementations;
- JDBC, Flyway, PostgreSQL and Testcontainers dependencies;
- CodeGraph as a mandatory step;
- automatic backlog selection;
- multi-epoch loop detection;
- automatic commits and notifications as acceptance evidence.

## Migration map

| Previous concern | New location or decision |
|---|---|
| Root `BENCHMARK_*.md` files | Ordered `benchmarks/` directory |
| Root and `agent/` control MDs | One JSON task plus concise memory |
| `.agent-orchestrator/runs` | Ignored `runtime/evidence/` |
| Large Python supervisors | Small `tools/orchestrator/` package |
| Many root shell scripts | `scripts/install`, `scripts/db`, `scripts/agent` |
| `notify-success.sh` at root | `scripts/notify-success.sh` |
| Custom Ollama Java clients | Removed; future Spring AI phase |
| Frontend/e2e | Removed until backend RAG is accepted |
| Native and Docker PostgreSQL variants | Docker default; one optional sudo installer |

## Phase sequence

Base → deterministic ingestion → pgvector → Spring AI RAG → thin API → optional UI.

The agentic harness is support infrastructure. It must not grow faster than the
application or become the main project.
