---
description: Rebuild the Riansares gallery section with Playwright and laptop 30B
mode: primary
model: ollama-laptop/qwen3-30b-coder-28k-6k-t33:latest
steps: 30
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
    "src/**": allow
    "public/**": allow
    "assets/**": allow
    "tests/**": allow
    "e2e/**": allow
    "**/*.html": allow
    "**/*.css": allow
    "**/*.scss": allow
    "**/*.js": allow
    "**/*.mjs": allow
    "**/*.ts": allow
    "**/*.tsx": allow
    "**/*.jsx": allow
    "**/*.vue": allow
    "**/*.astro": allow
    "**/*.svelte": allow
    "**/*.json": allow
    "**/*.md": allow
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
Keep input below 22K. Treat the public site as read-only. Inspect once, edit only the
local target section, validate once and stop. Never deploy, push or mutate remote state.
