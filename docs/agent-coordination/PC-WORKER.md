# PC code review (backend)

## Current evidence reviewed

- `runtime/ring-agent/ring/20260805T164348Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260805T164348Z/pc-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260805T164348Z/pc-runtime/memory.md`
- `runtime/ring-agent/ring/20260805T164348Z/pc-runtime/manifest.json`
- `runtime/ring-agent/ring/20260805T164348Z/pc-runtime/previous-ring-qwen3-directive.json`

## First current defect

`task-06e-child-process` is still **PENDING** while the active Codex packet is **REVISE** and includes unresolved mandatory instructions.

Key unresolved backend defect from Codex packet:

1. The prior approach used unsupported initializer loading (`-Dcontext.initializer.classes`) for this plain Spring Boot path.
2. The replacement service approach was type-incompatible for `KnowledgeIngestionService` constructor injection.

This means child-process verification remains unproven even though a gate summary artifact in this snapshot is green; no Codex ACCEPT is present for this task.

## Bounded next action for one worker pass

Apply the existing correction packet exactly within bounded scope:

- `src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionCliProcessIT.java`
- one test initializer class
- `src/test/resources/META-INF/spring.factories` (preserving existing entries)
- helper cleanup path (existing helper or nested helper)

Then rerun:

- `./scripts/task-gate.sh task-06e-child-process`

## Acceptance conditions (must all hold)

1. Exact gate exits 0: `./scripts/task-gate.sh task-06e-child-process`.
2. Codex review returns `ACCEPT` for `task-06e-child-process`.
3. No expansion into unrelated production code/scripts.
4. Do not reintroduce unsupported initializer loading or non-assignable replacement bean.

## Avoid repeating

- Repeating the rejected initializer path (`-Dcontext.initializer.classes`).
- Registering a bean that is not assignable to `KnowledgeIngestionService`.
- Running unchanged retries without new correction evidence.
