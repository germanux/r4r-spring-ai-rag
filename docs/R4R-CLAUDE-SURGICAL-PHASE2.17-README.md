# R4R Claude surgical phase 2.17

This update corrects false-success handling in `scripts/run-opencode-claude-surgical-review.sh`.

## Corrected behavior

- OpenCode architecture analysis is retried twice by default.
- A non-zero OpenCode exit or an unparseable report stops the chain before Claude.
- A non-zero Claude exit, empty result, invalid JSON result or explicit error result stops the chain before validation and Codex.
- The script no longer prints `completed` before evaluating model exit codes.
- Terminal states are explicit: `BLOCKED_OPENCODE`, `BLOCKED_CLAUDE`, `VALIDATION_FAILED`, `CODEX_REVIEW_FAILED`, `SUCCESS`, `SUCCESS_NO_CHANGES`.
- Shell syntax, Python compilation, Python unit tests and Codex each persist an exit-code file.
- Python tests use standard-library `unittest` discovery rather than silently skipping when `pytest` is unavailable.
- Exact selected executable paths are recorded in `source-state.txt`.
- Empty patches are reported as zero-byte evidence; they are not presented as applicable patches.

## Exit codes

- `0`: successful review/patch run
- `70`: OpenCode stage failed
- `71`: Claude Code stage failed
- `72`: deterministic validation failed
- `73`: requested Codex review failed

## OpenCode retries

Default: two attempts. Override with:

```bash
--opencode-retries 3
```

Every attempt is preserved as:

```text
opencode.attempt-N.raw.jsonl
opencode.attempt-N.stderr.log
opencode.attempt-N.analysis.md
```
