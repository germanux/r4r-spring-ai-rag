# R4R Phase 3 foundation — integration fan-out and worker guardian

This drop-in adds two deterministic control-plane capabilities:

1. `scripts/sync-agent-branches.sh` pins `agent/integration`, merges that exact commit
   into every active agent branch and pushes successful targets. Conflicting branches
   are restored and reported without contaminating the remaining targets. PC and LP
   use the existing dirty-state-preserving merge/restart transaction.
2. `scripts/run-ring-system.sh` and `py-ring-agent/run-ring-system.py` keep the PC and
   LP wrappers alive. `scripts/ensure-r4r-workers.sh` is idempotent and starts only a
   missing authoritative wrapper, so the laptop agent is no longer dependent on a
   manual terminal command.

## Normal use

From the integration worktree:

```bash
./scripts/sync-agent-branches.sh --fetch
```

The script pushes by default. Use `--no-push` for local-only verification and
`--dry-run` for a non-mutating plan.

## Supervisor

```bash
~/Desarrollo/r4r-ring-agent.git/scripts/run-ring-system.sh start
~/Desarrollo/r4r-ring-agent.git/scripts/run-ring-system.sh status
~/Desarrollo/r4r-ring-agent.git/scripts/run-ring-system.sh stop
```

## Every-minute synchronization

```cron
* * * * * /usr/bin/flock -n /tmp/r4r-agent-branch-sync.cron.lock /bin/bash -lc 'cd /home/german/Desarrollo/r4r-integration.git && ./scripts/sync-agent-branches.sh' >> /home/german/Desarrollo/r4r-agent-branch-sync.log 2>&1
```

The script also serializes itself with `/tmp/r4r-drive-import.lock`, preventing the
Google Drive rsync/autocommit job from modifying its worktree during a merge.

## Scope

This is the deterministic process-supervision portion of Phase 3. Ring remains the
technical decision-maker; the supervisor only manages Git fan-out and process liveness.
