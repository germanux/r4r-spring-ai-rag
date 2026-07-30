---
description: Implement one machine-selected R4R Spring AI RAG task on the PC
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

Read `AGENTS.md`, `.opencode/commands/task.md`, `.opencode/memory.md`, exactly the
selected task file named in the prompt, and the supplied Codex plan. Implement only
that task. A separate controller pass will require and verify actual `codegraph_*` MCP tool
calls before implementation. Use that structural report and do not replace it with
broad reading or prose-only claims. Do not edit controller, task, progress, memory
or gate files. Do not run Git write commands. Run the exact
task gate and finish with changed paths, current evidence and the first remaining
unproven condition.
