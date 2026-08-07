# Task 04 — Cited RAG backend for the Angular client

## Ownership

This task belongs to the already running PC/80B backend agent.

The laptop/gallery agent must not create the missing Java backend service. Its later
responsibility is the Angular UI and HTTP client, after the backend contract exists.

## Required outcome

Implement:

`src/main/java/com/riansares/r4r/rag/CitedRagService.java`

The service must:

1. Accept a non-blank user question.
2. Retrieve supporting chunks only through the existing Task 03
   `PgVectorKnowledgeStore`.
3. Abstain deterministically when retrieval provides no sufficient evidence.
4. Avoid calling the chat model during abstention.
5. Build a compact deterministic prompt from retrieved chunks.
6. Assign stable citation labels in retrieval order, such as `[S1]`, `[S2]`.
7. Call the configured Spring AI chat abstraction; do not implement an Ollama HTTP
   client.
8. Return a structured Java result containing:
   - generated answer;
   - abstention flag;
   - exact citations actually supplied to the model.
9. Derive citations from retrieved chunks, never from untrusted model-generated
   citation text.
10. Preserve source, heading path and ordinal for every returned citation.

## Angular integration boundary

Design the Java request/result contracts so a later REST adapter can expose them to an
Angular service without rewriting the RAG logic.

Do not implement Angular, HTML, CSS, JavaScript, Playwright or static web files in this
task. Do not create any `browser/` directory.

Do not add REST unless an already authorized Task 04 REST scaffold exists. Otherwise
leave the RAG service ready for a subsequent HTTP adapter task.

## Deterministic tests

Add focused tests proving:

- invalid and blank questions are rejected;
- retrieval order produces stable prompt citation labels;
- the answer is propagated from the chat abstraction;
- returned citations exactly match the retrieved evidence used in the prompt;
- insufficient evidence returns deterministic abstention;
- the chat abstraction is not called during abstention;
- no live Ollama connection is required.

Mock or fake only the retrieval/chat boundaries. Do not weaken Task 03 tests or alter
the task gate.

## Scope

Allowed product paths are the Task 04 RAG package and its focused tests. Preserve Task
01–03 behavior. Do not edit controller, progress, memory or Git history.

## Gate

Run exactly:

`./scripts/task-gate.sh task-04-rag`

Completion requires gate green and controller validation and global acceptance. Report changed paths, exact
Surefire/Failsafe totals, exit code and the first unproven condition.
