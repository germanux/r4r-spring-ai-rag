# FE-03E — Prove safe and accessible rendering

## Ownership and timebox

LP/frontend only. Target 45–70 minutes; hard ceiling 90 minutes.

## Objective

Prove that untrusted answer/citation text remains escaped and that the page exposes
stable accessible semantics.

## Required evidence

- HTML-like answer content is rendered as text and never executed/interpreted.
- Structured citation fields are escaped.
- Status and error regions use appropriate roles and deterministic text.
- Labels are associated with controls.
- Keyboard submission and disabled state remain usable.
- Citation-like text without structured citations does not create citation DOM.

## Exact gate

`./scripts/frontend-task-gate.sh task-fe-03e-security-accessibility`

## Completion

Gate `0`, Codex `ACCEPT`, controller commit:

`test(rag-ui): verify safe accessible rendering`
