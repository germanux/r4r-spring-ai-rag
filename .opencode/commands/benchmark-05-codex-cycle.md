# Benchmark 05 — Bounded Codex/OpenCode cycle

Validate one bounded orchestration cycle through `py-codex-agent`. Logs and evidence must
stay under `runtime/runs/<timestamp>/`. OpenCode edits only allowed task paths. Deterministic
gates own success. Codex is an optional strict-JSON reviewer; no auto-commit or auto-push.
Do not add retries, autonomous next-slice selection, worktrees, or hidden runtime folders.
