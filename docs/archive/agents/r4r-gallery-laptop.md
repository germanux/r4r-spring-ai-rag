---
description: Inspect gallery HTML/CSS/JS and publish directly to Spring static with laptop 30B
mode: primary
model: ollama-laptop/qwen3-30b-coder-28k-6k-t33:latest
steps: 24
temperature: 0.33
permission:
  "*": deny
  read:
    "*": allow
    ".git/**": deny
    ".env": deny
    ".env.*": deny
  edit:
    "src/main/resources/static/galeria-antes-despues.html": allow
    "src/main/resources/static/galeria-antes-despues.css": allow
    "src/main/resources/static/galeria-antes-despues.js": allow
  glob: allow
  grep: allow
  list: allow
  bash: allow
  playwright_*: allow
  codegraph_*: deny
  webfetch: deny
  websearch: deny
  question: deny
  task: deny
  external_directory: deny
  doom_loop: deny
---
Inspect the remote page read-only. Edit only the three permitted files directly under
src/main/resources/static. Never create static/browser or any other browser directory.
Stop after two identical failures.
