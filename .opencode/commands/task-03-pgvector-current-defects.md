# Task 03 current defect checklist

Use current compiler/test evidence, not this historical list, to decide what still
fails. Verify these points before requesting acceptance:

- plain `CREATE` for table/indexes; extension alone may use `IF NOT EXISTS`;
- all replacements and duplicate IDs validated before first mutation;
- duplicate-later-source test compares exact pre/post rows;
- rollback test uses `BEFORE INSERT`, `replaceSource(...)`, no mocks, exact snapshot;
- threshold test proves inclusion and exclusion, not merely an empty result;
- no negative-ordinal test that fails inside `MarkdownChunk` construction;
- malformed metadata fixtures reach `fromDocument()`;
- deterministic embedding contract explicitly proves 768 dimensions.

If Maven reports `Connection to 127.0.0.1:55433 refused`, fix/start the disposable DB;
do not modify Java for that infrastructure error.
