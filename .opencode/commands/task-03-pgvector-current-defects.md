# Task 03 — Current defect packet for the dirty PgVector snapshot

This packet is subordinate to the normative Task 03 implementation guide. Use
CodeGraph first, then verify each item against the current source and gate. Do not
assume an item remains unfixed after another attempt.

## Mandatory CodeGraph reconnaissance

Before broad reads, perform actual `codegraph_*` calls covering:

- `PgVectorKnowledgeStore` and `MarkdownChunk` invariants;
- all Task 03 integration tests;
- Flyway V3 and the PgVector YAML configuration;
- the real Spring AI `VectorStore` and `Document` API used by this project.

A prose claim that CodeGraph was used is not evidence.

## Defects observed in the supplied dirty snapshot

1. **Flyway drift is hidden.** `V3__pgvector_store.sql` uses `IF NOT EXISTS`
   for the application-owned table and indexes. Keep `CREATE EXTENSION IF NOT
   EXISTS vector`, but use plain `CREATE TABLE` and `CREATE INDEX` for objects
   owned by this migration.

2. **Rollback proof is simulated and prohibited.**
   `PgVectorKnowledgeStoreTransactionalRollbackIT` uses `@SpyBean`, Mockito,
   `doAnswer`, an `AtomicBoolean`, and a simulated `VectorStore.add(...)`
   exception. Replace it with a real PostgreSQL `BEFORE INSERT` trigger that
   raises after the delete has occurred inside the same transaction. Compare an
   exact, ordered snapshot of every relevant row before and after the failure,
   and always drop the trigger/function in cleanup.

3. **`index(...)` does not complete all prevalidation before mutation.** It
   groups and validates basic fields first, but builds `Document` objects and
   checks duplicate logical IDs one source at a time immediately before deleting
   that source. Prebuild immutable replacement batches for every represented
   source and reject all duplicate identities before the first `DELETE`.

4. **Duplicate identity lacks direct integration evidence.** Add a case where
   source A is valid and a later source contains duplicate logical identities.
   The call must fail and the complete pre-existing database snapshot for all
   involved sources must remain unchanged.

5. **Threshold test is one-sided.**
   `thresholdFilteringExcludesBelowMinScore()` only proves that `low.md` is
   absent. It must also prove that the expected relevant result remains in the
   strict result; otherwise an empty list is a false pass.

6. **Negative ordinal test is a false store test.**
   `rejectsNegativeOrdinalInIndex()` constructs the invalid `MarkdownChunk`
   inside the assertion lambda. The record constructor fails before
   `PgVectorKnowledgeStore.index(...)` is invoked. Remove this test from the
   store suite or move it to the chunk model suite.

7. **Null `Document` field tests may pass in the builder rather than the store.**
   The null-text and null-metadata fixtures are built inside assertion lambdas.
   If Spring AI rejects or normalizes them before `fromDocument(...)`, remove
   those false claims. Retain only malformed states that can genuinely reach
   `fromDocument(...)`.

8. **Malformed metadata coverage is incomplete.** Add reachable fixtures for at
   least a non-string `headingPath` element and a non-`Number` ordinal. These
   must fail in `fromDocument(...)` with clear `IllegalStateException` messages.

9. **The deterministic embedding contract is not asserted directly.** Add a
   focused test proving exactly 768 dimensions, deterministic output for equal
   text, and predictably distinguishable vectors for the controlled search
   fixtures. No live Ollama request may occur.

10. **The integration suite contains avoidable duplication.** Both
    `@BeforeEach` and `@AfterEach` truncate the same table, and several stable-ID
    tests overlap. Do not sacrifice required evidence, but remove redundant or
    misleading tests so the PostgreSQL suite remains comprehensible and
    [tractable].

11. **Reopen and reconcile all seven dirty files before the gate.** A green
    Maven build alone is insufficient. Run exactly:

    ```bash
    ./scripts/task-gate.sh task-03-pgvector
    ```

Report exact test totals and the first unproven condition, or `none`. Do not
perform Git writes.
