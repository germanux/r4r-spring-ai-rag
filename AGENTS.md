# Repository rules

## Scope

Work only in this repository and only on the active task in
`agent/shared/CURRENT_TASK.json`.

## Required start

1. Read this file.
2. Read `agent/shared/CURRENT_TASK.json`.
3. Read `agent/shared/MEMORY.md`.
4. Inspect `git status --short` and `git diff --stat`.
5. State the first unproven acceptance condition before editing.

## Editing discipline

- Change only paths listed in the active task.
- Make one coherent change at a time.
- Do not add web, REST, database or LLM integration before its benchmark phase.
- Do not create custom Ollama HTTP clients; Spring AI integration belongs to phase 4.
- Do not weaken tests to obtain green.
- Do not run Git write operations.
- Do not edit generated output or runtime evidence.

## Validation

The deterministic runner owns validation. Use only commands defined in the active
JSON task. Preserve exit codes and do not pipe Maven through `head`, `tail`, `grep`
or `tee`.

## Stop conditions

Stop when the active acceptance conditions are proven, a deterministic gate fails,
or required external infrastructure is unavailable. Record exact evidence in
`agent/shared/MEMORY.md`.
