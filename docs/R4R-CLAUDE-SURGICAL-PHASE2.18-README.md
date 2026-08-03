# R4R OpenCode → Claude surgical runner — phase 2.18

This drop-in corrects the two failures observed after phase 2.17:

1. OpenCode could run the temporary architecture agent without an explicit model and
   terminate with `Unexpected server error`.
2. Older runs could invoke Claude Code print mode without a reliably delivered prompt.

## Changes

- Resolves the OpenCode model from the selected immutable commit:
  `config/r4r-agents.json -> agents.PC.provider/model`.
- Falls back to the selected commit's `.opencode/agents/r4r-ring.md` model declaration.
- Supports an explicit `--opencode-model provider/model` override.
- Writes the model into the temporary OpenCode agent and also passes `--model` to
  `opencode run`.
- Delivers the full Claude task through standard input with a short positional query,
  matching Claude Code's supported piped-input execution pattern.
- Supports `--claude-bin PATH` and `R4R_CLAUDE_BIN`.
- De-duplicates multiple Claude installations and prefers one whose `auth status`
  succeeds.
- Fails before creating a worktree when no usable Claude authentication is present.
- Keeps fail-closed behavior by default.
- Adds an explicit `--allow-opencode-fallback` mode. When selected, OpenCode failure is
  retained as evidence and Claude independently inspects the repository. Success is
  reported as `SUCCESS_WITH_OPENCODE_FALLBACK`, never plain `SUCCESS`.
- Records `opencode.model` and `opencode.fallback-used` in every run.

## Recommended execution

```bash
./scripts/run-opencode-claude-surgical-review.sh \
  --repo ~/Desarrollo/r4r-ring-agent.git \
  --branch r4r-chatgpt \
  --mode patch \
  --codex-review \
  --opencode-retries 2 \
  --output-root "$PWD/runtime/claude-surgical" \
  --keep-worktree \
  --prompt "Corrige con cambios mínimos la captura de evidencias, gates exactos, bucles de permisos, clasificación de errores y ciclo stop-merge-restart."
```

If the local OpenCode provider remains unavailable after the model is pinned, rerun
with the explicit fallback:

```bash
./scripts/run-opencode-claude-surgical-review.sh \
  --repo ~/Desarrollo/r4r-ring-agent.git \
  --branch r4r-chatgpt \
  --mode patch \
  --codex-review \
  --opencode-retries 2 \
  --allow-opencode-fallback \
  --output-root "$PWD/runtime/claude-surgical" \
  --keep-worktree \
  --prompt "Corrige con cambios mínimos la captura de evidencias, gates exactos, bucles de permisos, clasificación de errores y ciclo stop-merge-restart."
```

The fallback is opt-in because it changes the two-model chain into a Claude-led audit
with the OpenCode provider failure preserved as an evidence limitation.
