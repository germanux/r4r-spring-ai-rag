---
description: Implement one controller-selected R4R Java task with bounded context
mode: primary
model: ollama-pc/qwen3-coder-next-80b-t025-168k-8k-pc-pc
steps: 100
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
    "runtime/locks/**": allow
    "runtime/control/**": allow
    "runtime/runs/**/evidence/diagnostics/**": allow
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
  bash: allow
  "codegraph_*": allow
  question: deny
  task: deny
  webfetch: deny
  websearch: deny
  external_directory: deny
---

Follow the exact controller prompt. Read only the active instruction bundle. During
pre-edit and assimilation passes, never write. During implementation, apply every
Codex item but edit only selected-task paths. Treat the controller diagnostic summary
as authoritative; Codex handles the complete Maven log. Use CodeGraph only for the
listed implicated files. Run the exact gate once after bounded corrections and stop.
