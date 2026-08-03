# FE-03B — Render answer and abstention states

## Ownership and timebox

LP/frontend only. Target 45–70 minutes; hard ceiling 90 minutes.

## Objective

Render the typed RAG response with deterministic success and abstention behavior.

## Required evidence

- A submitted question enters loading and disables duplicate submission.
- A non-abstained response renders the answer.
- An abstained response renders an explicit abstention message rather than a blank
  answer.
- Transport failure renders the deterministic error contract.
- Clear/reset returns the page to a stable idle state.
- Tests assert rendered DOM, not only component fields.

## Exact gate

`./scripts/frontend-task-gate.sh task-fe-03b-answer-abstention`

## Completion

Gate `0`, Codex `ACCEPT`, controller commit:

`feat(rag-ui): render answer and abstention states`
