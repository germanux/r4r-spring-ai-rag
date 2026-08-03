# FE-03A — Stabilize the current Angular RAG page baseline

## Ownership and timebox

LP/frontend only. Edit `frontend/**` and `docs/frontend/**`. Target 45–70 minutes;
hard ceiling 90 minutes.

## Objective

Preserve the useful RAG page already present and produce a clean compilable baseline
commit. Correct current TypeScript/template structure, whitespace and existing unit
failures without expanding scope.

## Required evidence

- Angular 17 build succeeds.
- Existing RAG page unit tests succeed in headless Chrome.
- The question form and explicit idle/loading/success/abstention/error state model
  remain present.
- `git diff --check` is clean.
- No backend, controller, progress or runtime file is edited.

Later subtasks own detailed answer rendering, citations, DOM assertions, escaping and
accessibility.

## Exact gate

`./scripts/frontend-task-gate.sh task-fe-03-rag-ui`

## Completion

Gate `0`, Codex `ACCEPT`, controller commit:

`chore(rag-ui): checkpoint compilable RAG page baseline`
