# R4R OpenCode dual surgical reviewer — phase 2.19

This replaces the paid Claude Code dependency with two local OpenCode agents:

1. `r4r-surgical-architect`: whole-repository, read-only diagnosis.
2. `r4r-surgical-fixer`: minimal edits in an isolated detached worktree.

The controller captures evidence, validates shell/Python code, emits a binary Git patch,
and can ask Codex for a final read-only review. Neither OpenCode agent writes Git history.

## Install

```bash
unzip -o r4r-opencode-dual-surgical-phase2.19-dropin.zip
chmod +x scripts/run-opencode-dual-surgical-review.sh
git add scripts/run-opencode-dual-surgical-review.sh \
  .opencode/agents/r4r-surgical-architect.md \
  .opencode/agents/r4r-surgical-fixer.md \
  R4R-OPENCODE-DUAL-SURGICAL-PHASE2.19-README.md
git commit -m "feat(surgical): add dual local OpenCode reviewer"
```

## Run both agents

```bash
./scripts/run-opencode-dual-surgical-review.sh \
  --repo ~/Desarrollo/r4r-ring-agent.git \
  --branch r4r-chatgpt \
  --mode patch \
  --codex-review \
  --output-root "$PWD/runtime/opencode-dual-surgical" \
  --keep-worktree \
  --prompt "Correct evidence capture, exact gates, permission loops, error classification and stop-merge-restart lifecycle."
```

Use `--mode review` to run only the architect.
