---
description: Implement one bounded R4R Spring AI RAG task on the PC
mode: primary
model: ollama-pc/qwen3-coder-next-80b-t025-168k-8k-pc-pc
steps: 80
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
  edit:
    ".opencode/MEMORY.md": allow
    "pom.xml": allow
    "src/**": allow
    "knowledge/**": allow
    "docker-postgres/**": allow
    "scripts/**": allow
    "docs/**": allow
    ".env.example": allow
    ".gitignore": allow
    "codegraph.json": allow
  glob: allow
  grep: allow
  bash: allow
  "codegraph_*": allow
  question: deny
  task: deny
  webfetch: deny
  websearch: deny
  external_directory: deny
---

Read `AGENTS.md`, `.opencode/CURRENT_TASK.json`, `.opencode/MEMORY.md`, and exactly
the active command named by the task. Work only on that bounded objective. Use
CodeGraph for impact analysis when a change spans several symbols or files. Do not
run Git write commands. Finish with changed paths, exact validation evidence, and
the first remaining unproven condition.
