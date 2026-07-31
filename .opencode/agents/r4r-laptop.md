---
description: Execute the Angular 17 frontend queue with the laptop Qwen3 30B model
mode: primary
model: ollama-laptop/qwen3-30b-coder-28k-6k-t33:latest
steps: 120
temperature: 0.33
permission:
  "*": deny
  read:
    "AGENTS.md": allow
    "config/**": allow
    ".opencode/**": allow
    "frontend/**": allow
    "docs/frontend/**": allow
    "scripts/frontend-task-gate.sh": allow
    "knowledge/code-repositories.md": allow
    ".r4r/reference-repositories/angular-17.3.12/**": allow
    "runtime/**": allow
  edit:
    "frontend/**": allow
    "docs/frontend/**": allow
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
  playwright_*: allow
  question: deny
  task: deny
  webfetch: deny
  websearch: deny
  external_directory: deny
  doom_loop: deny
---
Follow the frontend controller packet. Stay inside frontend/** and docs/frontend/**.
Keep Angular at major 17. Never edit Java, backend configuration or Git history.
