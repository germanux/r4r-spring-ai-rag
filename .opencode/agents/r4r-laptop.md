---
description: Implement one compact R4R task with the remote laptop 30B worker
mode: primary
model: ollama-laptop/qwen3-30b-coder-28k-6k-t33:latest
steps: 44
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
  codegraph_*: allow
  playwright_*: deny
  question: deny
  task: deny
  webfetch: deny
  websearch: deny
  external_directory: deny
  doom_loop: deny
---
The total window is 28K with up to 6K output, so keep working input below about 22K.
Read only the active packet and implicated files. Make one bounded repair batch. After
two identical failures, stop and report the blocker. Never write Git history.
