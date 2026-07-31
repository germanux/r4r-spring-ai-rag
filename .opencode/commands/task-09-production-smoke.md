# Task 09 — Packaged production RAG smoke acceptance

## Ownership

This is the final PC/backend runtime task for the current queue. It validates the
packaged Spring Boot application and the populated production RAG. It does not modify
the Angular frontend.

## Objective

Build the application from clean sources, launch the packaged JAR on an isolated port,
send real HTTP requests through `/api/rag/answers`, verify supported and unsupported
behavior, stop the process cleanly, and preserve reproducible evidence.

Starting successfully from IntelliJ or stale `target/classes` is not evidence for this
task.

## Required focused test

Create exactly:

`src/test/java/com/riansares/r4r/rag/api/RagProductionSmokeTest.java`

The deterministic test must use a Spring Boot random port and controlled retrieval/chat
boundaries. It must prove the HTTP-level assertions used by the production smoke
verifier:

- supported response is HTTP 200;
- `abstention` is false;
- answer is non-blank;
- citations are non-empty;
- every citation has `label`, `source`, `headingPath` and non-negative `ordinal`;
- unsupported response is HTTP 200 with deterministic abstention and empty citations;
- invalid requests remain 400;
- no live Ollama dependency in the test.

## Clean packaged build

Delete generated Maven output and run the canonical full backend gate. Then build the
repackaged JAR from current sources.

Evidence must show javac compiling current sources after the clean step. A log saying
`Nothing to compile - all classes are up to date` immediately after source changes is
not sufficient.

## Production launch

Load the repository `.env` without printing secrets. Start the packaged JAR on a
dedicated smoke port, default `18080`, and redirect its process log to a temporary file
under `runtime/`.

Requirements:

1. use the persistent application PostgreSQL database;
2. use the PC Ollama embedding and generation models;
3. wait for an observable readiness condition;
4. fail if the process exits before readiness;
5. retain the PID;
6. always stop the process through a trap/finally path;
7. confirm the port is closed after shutdown;
8. never kill unrelated Java processes.

Do not rely on an already-running IntelliJ process.

## Real HTTP acceptance

Choose one supported question from the Task 08 dataset and one unsupported question.

For the supported request, assert from parsed JSON:

- HTTP 200;
- `abstention == false`;
- non-blank `answer`;
- at least one citation;
- stable sequential labels beginning `[S1]`;
- each citation source exists in the managed corpus;
- heading path is present;
- ordinal is non-negative.

For the unsupported request, assert:

- HTTP 200;
- `abstention == true`;
- empty citations;
- no unsupported answer represented as grounded fact.

Also send one malformed or blank request and assert HTTP 400.

Do not validate JSON with `grep`. Parse it with a JSON parser.

## Evidence document

Create or replace:

`docs/backend/production-rag-smoke.md`

Record:

- timestamp and Git commit;
- clean build command and result;
- packaged JAR path and checksum;
- isolated port;
- sanitized startup configuration;
- readiness evidence;
- supported/unsupported question IDs;
- HTTP status codes;
- parsed response assertions;
- citation source list;
- process shutdown and closed-port evidence;
- warnings observed;
- remaining limitations.

Do not commit complete generated answers when they contain unnecessary source text.
A concise sanitized excerpt or hash is sufficient.

## Acceptance matrix

| ID | Requirement | Required evidence |
|---|---|---|
| A1 | Current sources compile cleanly | Clean javac/build log |
| A2 | Packaged JAR starts | PID and readiness evidence |
| A3 | Production database/model used | Sanitized startup configuration |
| A4 | Supported API result grounded | Parsed JSON and citation checks |
| A5 | Unsupported query abstains | Parsed JSON |
| A6 | Invalid request rejected | HTTP 400 |
| A7 | Process stops cleanly | PID/port shutdown evidence |
| A8 | Deterministic HTTP test exists | `RagProductionSmokeTest` |
| A9 | No regression | Full backend gate |
| A10 | Working tree coherent | Diff check and automatic task commit |

## Exact gate

Run exactly the controller-supplied clean gate. It requires
`RagProductionSmokeTest` to exist and pass.

Codex must also inspect the current packaged-runtime evidence before `ACCEPT`; a test
context alone cannot satisfy the production launch rows.

## Completion

Completion requires all ten acceptance rows, exact gate exit code `0`, a stopped smoke
process, Codex `ACCEPT`, automatic local commit and no push.
