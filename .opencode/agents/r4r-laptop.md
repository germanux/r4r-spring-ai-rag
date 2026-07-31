---
description: Execute one compact Java R4R task with remote laptop Qwen3 30B
mode: primary
model: ollama-laptop/qwen3-30b-coder-28k-6k-t33:latest
steps: 32
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
The total window is 28K with 6K reserved for output: keep effective input below 22K.
Read only the compact task packet and implicated files. Make one bounded repair batch,
run the exact gate once and stop after two identical failures. Never write Git history.
