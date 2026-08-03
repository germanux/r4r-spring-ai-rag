# R4R worktree branch synchronization v3

Files:

- `AGENTS.md`: restores the 45–70 minute target, 90 minute hard ceiling and one-gate/one-commit subtask policy for PC, LP and Ring.
- `scripts/sync-agent-branches.sh`: no-argument full worktree-aware fetch, sequential centralization, round propagation and pushes.
- `scripts/install-r4r-branch-sync-systemd.sh`: installs the 3-minute user timer and invokes the sync script without flags.

Default sync discovery uses `git worktree list --porcelain` from the shared Git common directory. Every non-detached worktree branch participates except the hub branch itself. Use `--exclude PATTERN` for an explicit opt-out.

PC and LP are converged once at the end through the existing safe worker merger to avoid restarting active workers once per source branch. Other subscribed worktrees are propagated after every source round.

## Live agent consoles

Open each block in a separate terminal. Press `Ctrl+C` to stop following a log.

### Ring Agent

```bash
cd ~/Desarrollo/r4r-ring-agent.git

tail -n 200 -F \
  runtime/ring-system/ring-agent.console.log
```

### PC backend agent

```bash
cd ~/Desarrollo/r4r-ring-agent.git

PC_LOG="$(
  {
    find runtime/ring-agent/pc \
      -type f -name '*.log' -printf '%T@ %p\n' 2>/dev/null
    find runtime/ring-agent/bootstrap \
      -type f -name '*-PC.log' -printf '%T@ %p\n' 2>/dev/null
  } |
  sort -nr |
  cut -d' ' -f2- |
  head -n 1
)"

test -n "$PC_LOG" || {
  echo 'No PC agent console log found.' >&2
  exit 1
}

printf 'Following PC console: %s\n' "$PC_LOG"
tail -n 200 -F "$PC_LOG"
```

### LP frontend agent

```bash
cd ~/Desarrollo/r4r-ring-agent.git

LP_LOG="$(
  {
    find runtime/ring-agent/lp \
      -type f -name '*.log' -printf '%T@ %p\n' 2>/dev/null
    find runtime/ring-agent/bootstrap \
      -type f -name '*-LP.log' -printf '%T@ %p\n' 2>/dev/null
  } |
  sort -nr |
  cut -d' ' -f2- |
  head -n 1
)"

test -n "$LP_LOG" || {
  echo 'No LP agent console log found.' >&2
  exit 1
}

printf 'Following LP console: %s\n' "$LP_LOG"
tail -n 200 -F "$LP_LOG"
```
