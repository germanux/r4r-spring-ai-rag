---
description: Execute one compact R4R task with remote laptop Qwen3 30B
mode: primary
model: ollama-laptop/qwen3-30b-coder-28k-6k-t33:latest
steps: 120
temperature: 0.33
permission:
  "*": deny
  read:
    "AGENTS.md": allow
    ".opencode/**": allow
    "pom.xml": allow
    "src/**": allow
    "knowledge/**": allow
    "docker-postgres/**": allow
    "scripts/**": allow
    "docs/**": allow
    ".env.example": allow
    ".gitignore": allow
    "codegraph.json": allow
    "runtime/**": allow
  edit:
    "pom.xml": allow
    "src/**": allow
    "knowledge/**": allow
    "docker-postgres/**": allow
    "docs/**": allow
    ".env.example": allow
    ".gitignore": allow
    "codegraph.json": allow
  glob: allow
  grep: allow
  list: allow
  bash: allow
  codegraph_*: deny
  playwright_*: deny
  question: deny
  task: deny
  webfetch: deny
  websearch: deny
  external_directory: deny
  doom_loop: deny
---
The laptop route uses the repository's compact direct worker instead of the full
OpenCode tool loop. The worker receives a bounded Codex packet and selected source
files, returns complete task-scoped file contents, validates every path locally and
lets the Python controller run the exact gate. It never writes Git history.
