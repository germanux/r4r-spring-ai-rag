---
description: Execute one bounded Java R4R task with the PC 80B model
mode: primary
model: ollama-pc/qwen3-coder-next-80b-t033-128k-8k-pc-pc:latest
steps: 64
temperature: 0.25
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
  codegraph_*: allow
  playwright_*: deny
  question: deny
  task: deny
  webfetch: deny
  websearch: deny
  external_directory: deny
  doom_loop: deny
---
Follow the controller packet exactly. Edit only active-task product paths. Make one
bounded repair batch, rerun the exact gate once and stop after two identical failures.
Never write Git history.
