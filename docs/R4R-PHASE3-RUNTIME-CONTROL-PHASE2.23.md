# R4R Phase 3 runtime/control correction — phase 2.23

This correction addresses failures observed while `agent/integration` was being
propagated under cron.

## Corrected defects

1. **Split Ring runtime identity**

   Branch discovery could select a worktree checking out
   `agent/ring-agent-worker` instead of the operational
   `~/Desarrollo/r4r-ring-agent.git` path. The wrapper then read one JSONC control
   file while the merge script wrote another. Runtime identity is now selected by
   canonical path first; branch checkout is only a fallback.

2. **Hard-coded wrapper paths**

   `run-worker-streamed.py` now honours `R4R_RING_WORKTREE`,
   `R4R_PC_WORKTREE`, `R4R_LP_WORKTREE` and `R4R_DEVELOPMENT_ROOT`.
   Guardian and merge launchers pass these values explicitly and start each wrapper
   with the canonical Ring directory as its current working directory.

3. **Managed worker that does not acknowledge stop**

   The merge flow first requests a normal JSONC stop. After a bounded grace period
   it sends SIGTERM only to the identified managed wrapper/controller process tree;
   if that tree still remains, it escalates to SIGKILL. Arbitrary unrelated
   processes are not targeted. Set `R4R_FORCE_STOP_AFTER_SECONDS=0` to disable this
   escalation.

4. **OpenCode unavailable under cron/nohup**

   The merge launcher now sources `scripts/r4r-runtime-env.sh`, exports the
   canonical worktree variables, validates Node/OpenCode/Codex and passes the
   resolved environment into the new wrapper session.

5. **Supervisor race after failed merge/restart**

   `sync-agent-branches.sh` no longer starts the persistent guardian after a worker
   merge/restart failure.

6. **Non-interactive GitHub authentication**

   Manual `--push` remains strict. The new `--push-if-available` mode attempts a
   non-interactive push once and continues with local synchronization when no
   credentials are available. The cron installer uses this mode.

## Canonical cron

`install-r4r-automation-cron.sh` installs one branch-sync line using:

```text
R4R_RING_WORKTREE=~/Desarrollo/r4r-ring-agent.git
sync-agent-branches.sh --push-if-available
```

Google Drive import/autocommit entries are preserved.

## Relevant status meanings

- A conflict in `r4r-chatgpt` is isolated and the branch is restored.
- PC/LP are not restarted until preserved state has been restored and verified.
- Remote push unavailability in best-effort mode does not invalidate local merges.
- A worker synchronization failure prevents supervisor startup for that cycle.
