# R4R hub branch synchronization v2

## Data flow

1. Every selected active branch is merged sequentially into `agent/integration`.
2. The resulting integration commit is pinned once.
3. That exact commit is propagated back to every selected branch.
4. Integration is pushed first; updated target branches are pushed afterwards.

Default branches are local `agent/*` plus `r4r-chatgpt`, excluding the hub,
`main`, `master`, `backup/*`, `agent/snapshots` and obsolete Claude surgical
branches.

## Conflict behavior

Collection conflicts are deliberately left open in:

```text
/home/german/Desarrollo/r4r-integration.git
```

The script stops before fan-out, writes a report under:

```text
/home/german/Desarrollo/.r4r-runtime/branch-sync/conflicts/
```

and sends a critical desktop notification. On the first occurrence it also
copies the path to the clipboard and opens the affected directory when desktop
utilities are available.

Resolve:

```bash
cd /home/german/Desarrollo/r4r-integration.git
git status
# edit the conflicted files
git add <files>
git commit
```

Abort:

```bash
git -C /home/german/Desarrollo/r4r-integration.git merge --abort
```

## Manual validation

```bash
cd /home/german/Desarrollo/r4r-integration.git

./scripts/sync-agent-branches.sh \
  --fetch \
  --push \
  --dry-run \
  --no-guardian
```

Real run:

```bash
./scripts/sync-agent-branches.sh --fetch --push
```

## Three-minute user timer

```bash
./scripts/install-r4r-branch-sync-systemd.sh
```

Inspect:

```bash
systemctl --user status r4r-agent-branch-sync.timer --no-pager
systemctl --user list-timers r4r-agent-branch-sync.timer --no-pager
journalctl --user -u r4r-agent-branch-sync.service -f
```

The installer removes only the previous `R4R_AGENT_BRANCH_SYNC` cron line, so
the systemd timer is the sole scheduler for this synchronization.
