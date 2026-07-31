# Task 01 — Java 21 Spring Boot baseline

## Ownership

Owner: PC backend agent.

This task preserves the accepted backend baseline. It must not introduce new product
features.

## Required outcome

Verify that the existing Java 21 non-web Spring Boot application:

1. Compiles successfully.
2. Packages successfully.
3. Starts with the expected Spring configuration.
4. Preserves the existing PostgreSQL, Flyway and Spring AI configuration.
5. Keeps all current deterministic unit and integration tests green.

## Scope

Allowed paths:

- pom.xml
- src/main/**
- src/test/**
- docker-postgres/**
- .env.example
- docs/backend/**

Only repair defects required to restore the accepted baseline.

## Forbidden work

Do not add:

- REST controllers;
- HTTP endpoints;
- Angular;
- HTML, CSS or TypeScript;
- Playwright;
- new product features;
- handwritten Ollama HTTP clients;
- unrelated refactors.

REST and frontend work belong to later dedicated tasks.

## Gate

Run exactly:

`./scripts/task-gate.sh task-01-base`

Completion requires:

- gate exit code 0;
- all existing tests green;
- no unrelated product changes.
