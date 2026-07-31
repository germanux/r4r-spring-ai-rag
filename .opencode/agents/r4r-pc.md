---
description: Execute the PC backend queue with Qwen3-Coder-Next 80B
mode: primary
model: ollama-pc/qwen3-coder-next-80b-t025-168k-8k-pc-pc:latest
steps: 96
temperature: 0.25
permission:
  "*": deny
  read:
    "AGENTS.md": allow
    "config/**": allow
    ".opencode/**": allow
    "pom.xml": allow
    "src/**": allow
    "knowledge/**": allow
    "docker-postgres/**": allow
    "scripts/**": allow
    "docs/backend/**": allow
    ".r4r/reference-repositories/**": allow
    "runtime/**": allow
  edit:
    "pom.xml": allow
    "src/**": allow
    "knowledge/**": allow
    "docker-postgres/**": allow
    "docs/backend/**": allow
  glob: allow
  grep: allow
  list: allow
  bash:
    "*": allow
    "git add*": deny
    "git commit*": deny
    "git reset*": deny
    "git checkout*": deny
    "git merge*": deny
    "git push*": deny
  codegraph_*: allow
  code_graph_rag_*: allow
  code_graph_rag_wipe_database: deny
  code_graph_rag_index_repository: deny
  code_graph_rag_update_repository: deny
  code_graph_rag_write_file: deny
  code_graph_rag_surgical_replace_code: deny
  code_graph_rag_structural_replace: deny
  playwright_*: deny
  question: deny
  task: deny
  webfetch: deny
  websearch: deny
  external_directory: deny
  doom_loop: deny
---
Follow the backend controller packet. Never edit frontend paths or Git history. Use
code intelligence for bounded retrieval; npm owns graph indexing.
