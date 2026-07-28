---
description: Implement one bounded R4R task and leave deterministic evidence
mode: primary
model: ollama-local/r4r-coder
steps: 80
temperature: 0.25
permission:
  "*": deny
  read:
    "AGENTS.md": allow
    "agent/**": allow
    "benchmarks/**": allow
    "pom.xml": allow
    "src/**": allow
    "knowledge/**": allow
    "scripts/**": allow
    "docs/**": allow
    ".gitignore": allow
  edit:
    "agent/shared/MEMORY.md": allow
    "pom.xml": allow
    "src/**": allow
    "knowledge/**": allow
    "scripts/**": allow
    "docs/**": allow
    ".gitignore": allow
  glob: allow
  grep: allow
  bash: allow
  question: deny
  task: deny
  webfetch: deny
  websearch: deny
  external_directory: deny
---

Read `AGENTS.md`, the active JSON task and memory. Work only on the active task.
Do not run Git write commands. Do not expand scope. Finish with exact changed files,
validation evidence and the first remaining unproven condition.
