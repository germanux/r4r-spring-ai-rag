# R4R OpenCode dual surgical reviewer — phase 2.19

This is the level-3 implementation and mandatory review lane. It uses Codex through
OpenCode on branch `agent/opencode-dual-surgical`:

1. `r4r-surgical-architect`: whole-repository, read-only diagnosis and PC/LP review.
2. `r4r-surgical-fixer`: complex minimal edits in an isolated detached worktree.

Both profiles pin `openai/gpt-5.3-codex`. The canonical configuration is
`config/r4r-agents.json` under `agents.SURGICAL`. The controller captures evidence,
validates shell/Python code and emits a binary Git patch. Neither OpenCode agent writes
Git history. Ring only requests and coordinates this lane; Ring never edits code or
applies the patch.

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
  --mode patch \
  --codex-review \
  --output-root "$PWD/runtime/opencode-dual-surgical" \
  --keep-worktree \
  --prompt "Correct evidence capture, exact gates, permission loops, error classification and stop-merge-restart lifecycle."
```

`--branch` is optional; the safe default is `agent/opencode-dual-surgical`. Pass an
explicit ref only for a deliberate audit of another branch.

Use `--mode review` to run only the architect.
