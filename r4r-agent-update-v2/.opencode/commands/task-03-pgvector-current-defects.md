# Task 03 — Current defect packet for the 2026-07-30 dirty snapshot

This companion file is a bounded correction packet for the current uncommitted
Task 03 implementation. Reconcile it with the normative implementation guide,
the actual source tree, CodeGraph and the current gate. Do not treat this file as
proof that a defect still exists after editing.

Before broad source reads, use the mandatory CodeGraph reconnaissance to inspect:

- `PgVectorKnowledgeStore` and its callers;
- `MarkdownChunk` constructor invariants;
- `PgVectorKnowledgeStoreIT`;
- `PgVectorKnowledgeStoreTransactionalRollbackIT`;
- Flyway V3 and the Spring AI PgVector configuration.

The uploaded dirty snapshot contains these concrete defects:

1. `V3__pgvector_store.sql` uses `IF NOT EXISTS` for the application-owned table
   and indexes. The normative guide requires plain `CREATE` so Flyway exposes
   schema drift.
2. `PgVectorKnowledgeStoreTransactionalRollbackIT` uses `@SpyBean`, Mockito,
   `doAnswer`, a simulated `VectorStore.add(...)` exception and a raw hand-built
   768-value vector insert. All are explicitly prohibited. Replace the test with
   the real PostgreSQL `BEFORE INSERT` trigger proof and exact ordered snapshot
   equality.
3. `thresholdFilteringExcludesBelowMinScore()` proves only that the irrelevant
   source is absent. It must also prove that the expected relevant source remains
   present in the strict result.
4. `rejectsNegativeOrdinalInIndex()` constructs the invalid `MarkdownChunk`
   inside the AssertJ lambda. The record constructor rejects the value before the
   store is invoked, so this is not a store test. Remove or relocate it to the
   `MarkdownChunk` test suite.
5. The null-text and null-metadata retrieval tests construct `Document` fixtures
   inside assertion lambdas. If the Spring AI builder rejects those fixtures,
   remove the false store-level claims. Keep only malformed states that can
   actually reach `fromDocument(...)`.
6. `index(...)` validates chunk fields before mutation, but document construction
   and duplicate logical-ID detection happen one source at a time immediately
   before each delete. Prebuild and validate every source replacement, including
   duplicate IDs, before the first database mutation.
7. Add a focused test proving duplicate logical identities are rejected before
   any source row changes. The current snapshot has production duplicate checking
   but no direct integration proof.

Do not solve only the first defect. Map every item to an exact source change or a
justified no-change decision, then run exactly:

```bash
./scripts/task-gate.sh task-03-pgvector
```
