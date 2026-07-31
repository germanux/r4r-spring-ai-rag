---
description: Inspect gallery HTML/CSS/JS and publish directly to Spring static with PC 80B
mode: primary
model: ollama-pc/qwen3-coder-next-80b-t025-168k-8k-pc-pc
steps: 36
temperature: 0.25
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
