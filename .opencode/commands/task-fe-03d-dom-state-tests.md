# FE-03D — Prove interaction states through the DOM

## Ownership and timebox

LP/frontend only. Target 45–70 minutes; hard ceiling 90 minutes.

## Objective

Replace property-only confidence with fixture-level DOM evidence.

## Required evidence

Using `fixture.detectChanges()` and rendered queries, prove:

- loading uses `role="status"`;
- textarea and submit control are disabled while loading;
- transport failure uses `role="alert"`;
- answer and abstention text are visible in their intended states;
- reset removes stale result/error/citation content.

Do not weaken existing service tests and do not require a live backend or LLM.

## Exact gate

`./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

## Completion

Gate `0`, Codex `ACCEPT`, controller commit:

`test(rag-ui): verify DOM interaction states`
