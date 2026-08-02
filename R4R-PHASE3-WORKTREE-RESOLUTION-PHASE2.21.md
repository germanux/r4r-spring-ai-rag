# R4R Phase 2.21 — authoritative runtime worktree resolution

This correction addresses the first real Phase-3 integration run after commit `4504b10`.

## Corrected defects

1. `sync-agent-branches.sh` no longer requires `agent/ring-agent-worker` to be checked
   out in a dedicated worktree. It resolves the operational Ring runtime in this order:
   the branch worktree when present, `R4R_RING_WORKTREE`, the canonical
   `~/Desarrollo/r4r-ring-agent.git`, then the script worktree when suitable.
2. PC and LP can therefore receive the pinned `agent/integration` commit through
   `merge-worker-branches-and-restart.sh` even while the canonical Ring worktree is on
   `r4r-chatgpt`.
3. Missing-worktree diagnostics now name Ring, PC and LP separately.
4. `--push` is accepted explicitly; pushing remains the default.
5. The persistent worker supervisor may execute code from `agent/integration` while
   storing runtime and supervising wrappers in the canonical Ring worktree.
6. `run-ring-system.py` accepts an explicit external guardian script.

## Safety properties retained

- one pinned integration commit per run;
- dirty regular branches remain untouched;
- conflict isolation and branch restoration;
- PC/LP backup, stash, merge, restoration and restart through the established merger;
- one guardian process and authoritative PC/LP worktrees;
- no forced reset or clean of user changes.

## Verification

- Bash syntax checks for the synchronization and supervisor launchers.
- Python compilation.
- Two supervisor unit tests, including code/runtime separation.
- Existing fan-out and conflict-isolation self-test.
- Synthetic four-worktree test proving that a canonical Ring worktree on
  `r4r-chatgpt` can supervise and merge the PC/LP branches.
