# Task 06C — Prove the non-web Spring lifecycle

## Ownership and timebox

PC/backend only. Target 45–70 minutes; hard ceiling 90 minutes.

## Objective

Prove that the production CLI creates and closes the intended Spring context, never
starts Tomcat and never causes normal application startup to ingest automatically.

## Required implementation

- Force `WebApplicationType.NONE` for the CLI.
- Close the context on success and failure.
- Replace production beans in tests before instantiation with a deterministic
  registry-level mechanism; do not rely on late singleton replacement.
- Prove normal `R4rSpringAiRagApplication` startup does not invoke ingestion.
- Do not add an administrative HTTP ingestion endpoint.

Create or complete:

`src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionSpringLifecycleTest.java`

## Exact gate

`./scripts/task-gate.sh task-06c-spring-lifecycle`

## Completion

Gate `0`, Codex `ACCEPT`, controller commit:

`fix(ingestion): enforce non-web Spring lifecycle`
