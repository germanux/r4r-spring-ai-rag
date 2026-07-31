---
description: Execute one compact R4R task with remote laptop Qwen3 30B
mode: primary
model: ollama-laptop/qwen3-30b-coder-28k-6k-t33:latest
steps: 12
temperature: 0.20
permission:
  "*": deny
---
The laptop route uses the repository's compact direct worker instead of the full
OpenCode tool loop. The worker receives a bounded Codex packet and selected source
files, returns complete task-scoped file contents, validates every path locally and
lets the Python controller run the exact gate. It never writes Git history.
