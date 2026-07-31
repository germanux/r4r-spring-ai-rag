---
description: Rebuild the Riansares gallery section with Playwright and laptop 30B
mode: primary
model: ollama-laptop/qwen3-30b-coder-28k-6k-t33:latest
steps: 40
temperature: 0.33
permission:
  "*": deny
  read:
    "*": allow
    ".git/**": deny
    ".env": deny
    ".env.*": deny
    ".env.example": allow
  edit:
    "**/*.html": allow
    "**/*.css": allow
    "**/*.scss": allow
    "**/*.sass": allow
    "**/*.less": allow
    "**/*.js": allow
    "**/*.mjs": allow
    "**/*.cjs": allow
    "**/*.ts": allow
    "**/*.tsx": allow
    "**/*.jsx": allow
    "**/*.vue": allow
    "**/*.astro": allow
    "**/*.svelte": allow
    "**/*.json": allow
    "**/*.md": allow
    "public/**": allow
    "assets/**": allow
    "src/**": allow
    "tests/**": allow
    "e2e/**": allow
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
The 28K window leaves about 22K for input when reserving 6K output. Inspect one page,
one target section and only directly relevant local files. The public site is read-only.
After two identical failures, stop and report the exact blocker.
