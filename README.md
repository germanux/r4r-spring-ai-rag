# R4R worktree branch synchronization v3

Files:

- `AGENTS.md`: restores the 45–70 minute target, 90 minute hard ceiling and one-gate/one-commit subtask policy for PC, LP and Ring.
- `scripts/sync-agent-branches.sh`: no-argument full worktree-aware fetch, sequential centralization, round propagation and pushes.
- `scripts/install-r4r-branch-sync-systemd.sh`: installs the 3-minute user timer and invokes the sync script without flags.

Default sync discovery uses `git worktree list --porcelain` from the shared Git common directory. Every non-detached worktree branch participates except the hub branch itself. Use `--exclude PATTERN` for an explicit opt-out.

PC and LP are converged once at the end through the existing safe worker merger to avoid restarting active workers once per source branch. Other subscribed worktrees are propagated after every source round.
