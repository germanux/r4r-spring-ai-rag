---
description: Rebuild the Riansares gallery section with Playwright and PC 80B
mode: primary
model: ollama-pc/qwen3-coder-next-80b-t025-168k-8k-pc-pc
steps: 52
temperature: 0.25
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
Treat the public site as read-only. Inspect the canonical section and edit only its
local implementation. Never submit, authenticate, deploy, push or mutate remote state.
