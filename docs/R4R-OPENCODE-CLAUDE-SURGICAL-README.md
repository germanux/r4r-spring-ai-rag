# R4R OpenCode → Claude Code surgical reviewer

This drop-in adds an independent whole-branch audit pipeline:

1. Resolve and pin one local Git branch/tag/commit.
2. Create a detached temporary Git worktree.
3. Mask likely credential-bearing files.
4. Run a read-only OpenCode architecture pass across the complete repository.
5. Run Claude Code in either read-only `review` mode or editable `patch` mode.
6. In patch mode, emit a binary-safe Git patch and run deterministic syntax/controller tests.
7. Optionally submit the evidence to Codex in read-only mode.
8. Remove the temporary worktree unless `--keep-worktree` is used.

It never changes the selected source branch and never creates Git history.

## Direct usage

```bash
cd ~/Desarrollo/r4r-ring-agent.git

./scripts/run-opencode-claude-surgical-review.sh \
  --repo . \
  --branch r4r-chatgpt \
  --mode review \
  --prompt 'Audit all Ring, PC, LP, OpenCode and Codex lifecycle paths.'
```

Generate corrections in the isolated worktree:

```bash
./scripts/run-opencode-claude-surgical-review.sh \
  --repo . \
  --branch r4r-chatgpt \
  --mode patch \
  --codex-review \
  --prompt 'Correct evidence capture, exact gates, repeated permission loops and restart lifecycle.'
```

Results are written under:

```text
runtime/claude-surgical/<UTC-RUN-ID>/
```

Apply a reviewed patch manually only after inspecting it:

```bash
git apply --check runtime/claude-surgical/<RUN-ID>/changes.patch
git apply runtime/claude-surgical/<RUN-ID>/changes.patch
```

## Optional OpenCode subagent

The included agent is:

```text
.opencode/agents/r4r-claude-surgical-reviewer.md
```

The existing snapshot-only `r4r-ring` agent intentionally remains unchanged. Do not
give it autonomous access to this high-impact subagent until its operator-control
contract has an explicit branch, mode and objective. For manual use, select the subagent
from OpenCode or invoke the shell script directly.

## Requirements

- Git
- Python 3
- OpenCode
- Claude Code authenticated locally
- Optional: Codex CLI for `--codex-review`

Claude Code supports non-interactive `-p`, JSON output, tool restrictions and permission
modes. OpenCode supports Markdown subagents and granular command permissions.
