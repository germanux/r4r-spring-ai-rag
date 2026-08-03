# FE-03C — Render structured citations

## Ownership and timebox

LP/frontend only. Target 45–70 minutes; hard ceiling 90 minutes.

## Objective

Render backend-provided structured citations without parsing model-authored text.

## Required evidence

- Citations are ordered deterministically by ordinal.
- Source, ordinal and ordered heading path are visible.
- No citation block is shown when the structured citation array is empty.
- Citation-like answer text is never parsed into a citation.
- Tests query the rendered citation DOM and prove ordering and content.

## Exact gate

`./scripts/frontend-task-gate.sh task-fe-03c-citations`

## Completion

Gate `0`, Codex `ACCEPT`, controller commit:

`feat(rag-ui): render structured citations`
