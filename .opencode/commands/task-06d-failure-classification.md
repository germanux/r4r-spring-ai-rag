# Task 06D — Classify ingestion failures by concrete cause

## Ownership and timebox

PC/backend only. Target 45–70 minutes; hard ceiling 90 minutes.

## Objective

Make CLI failure classification deterministic and type-based.

## Required implementation

- Walk the complete cause chain.
- Classify concrete infrastructure families such as JDBC/data-access, connection and
  timeout failures by `instanceof`, not package-name or message heuristics.
- Keep invalid configuration/missing corpus separate from infrastructure and generic
  ingestion failures.
- Preserve concise stderr output and never print credentials, embeddings or content.
- Do not broaden catch blocks so far that product defects are mislabeled as
  infrastructure failures.

Create or complete:

`src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionFailureClassificationTest.java`

## Exact gate

`./scripts/task-gate.sh task-06d-failure-classification`

## Completion

Gate `0`, controller validation and global acceptance:

`fix(ingestion): classify infrastructure failures`
