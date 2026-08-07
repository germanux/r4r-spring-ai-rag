# FE-03F — Complete Angular RAG page validation

## Ownership and timebox

LP/frontend only. Target 45–70 minutes; hard ceiling 90 minutes.

## Objective

Run the complete Angular build and deterministic unit proof after FE-03A–FE-03E.
This is a validation subtask; make only corrections directly proven by the first
current failure.

## Required evidence

- Angular 17 production build succeeds.
- All headless unit tests succeed.
- Answer, abstention, error, citations, DOM state, escaping and accessibility
  assertions are present and green.
- `git diff --check` is clean.
- No live backend or LLM is required.

## Exact gate

`./scripts/frontend-task-gate.sh task-fe-03f-final-validation`

## Completion

Gate `0`, controller validation and global acceptance:

`test(rag-ui): complete RAG page validation`
