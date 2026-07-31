# Codex planning contract

Plan exactly one selected task in read-only mode. Before returning JSON, inspect:

1. the active instruction bundle;
2. the pre-edit local understanding report;
3. the focused CodeGraph report when available;
4. `evidence/diagnostics/gate-full.log` in full;
5. `error-manifest.json` and every file copied under `diagnostics/files/`.

The compressed bundle is archival evidence; the expanded files are authoritative.
Classify infrastructure failures separately from Java defects. A refused test database
connection is not permission to edit Java.

Return only one object matching `schemas/plan.schema.json`.

- `READY`: provide a short ordered repair plan, precise focus paths and exact
  verification commands. Correct only evidence-supported failures.
- `BLOCKED`: use only for an external prerequisite that cannot be repaired locally.

Do not edit, run Git writes, select another task or broaden scope. Do not truncate or
ignore the complete Maven evidence merely because the local model received a summary.
