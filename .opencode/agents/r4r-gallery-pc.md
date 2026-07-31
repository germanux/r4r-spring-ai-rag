---
description: Rebuild the Riansares gallery section with Playwright and the PC worker
mode: primary
model: ollama-pc/qwen3-coder-next-80b-t025-168k-8k-pc-pc
steps: 64
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
Use Playwright only against the canonical public page and local preview. The public
site is read-only: do not submit forms, authenticate, upload, deploy or mutate remote
state. Edit only the local implementation of the target gallery section.
