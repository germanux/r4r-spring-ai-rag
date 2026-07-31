# R4R agent rules

## Scope

Use one selected agent and one bounded task. PC and laptop names select inference only;
OpenCode, Playwright, Git, builds and files run on the machine launching OpenCode.
Never run two agents against the same worktree.

## General discipline

- Read the active task file before editing.
- Read only files needed for that task; do not crawl the repository.
- Make the smallest coherent patch. Preserve public contracts and unrelated sections.
- After two identical tool errors, stop and report the blocker; never repeat blindly.
- No `sudo`, package-manager mutation, Git writes, push, deployment or secret reads.
- Run the repository's existing deterministic checks. Do not weaken tests or gates.

## RAG controller tasks

For Tasks 01-04 follow `.opencode/task-plan.json`, the selected command and the Codex
packet. Use CodeGraph only for implicated Java paths. Browser tools are forbidden.
A task advances only on its exact green gate and Codex `ACCEPT`.

## Gallery task

Canonical URL: `https://riansares4r.com/galeria-antes-despues`.
The XPath `/html/body/main/section[2]` is only a hint. Confirm the target semantically by
the heading `Trabajos realizados`; preserve the preceding hero and all later sections.

Use Playwright to inspect the target section, DOM, computed styles, responsive layout,
console and directly loaded assets. Do not copy the whole site's CSS or JavaScript.
Reuse local tokens/components first; transfer only rules or behavior required by the
section. Do not interact with forms, WhatsApp, cookies, authentication or remote state.

Edit only a local source implementation of `/galeria-antes-despues`. If that route or
its source cannot be found in the selected source worktree, stop without creating a
parallel website. Validate the local build and existing browser/e2e checks. Do not
deploy; report changed files, checks, visual differences and the first unproven item.
