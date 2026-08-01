#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

# ---------------------------------------------------------------------------
# EDIT THESE PATHS HERE. No command-line parameters are required.
# ---------------------------------------------------------------------------
DEVELOPMENT_ROOT = Path.home() / "Desarrollo"
RING_WORKTREE = DEVELOPMENT_ROOT / "r4r-ring-agent.git"
PC_WORKTREE = DEVELOPMENT_ROOT / "r4r-pc-worker.git"
LP_WORKTREE = DEVELOPMENT_ROOT / "r4r-lp-worker.git"
RUN_ONCE = True

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "src"))

from r4r_ring_agent.ring_loop import run_ring_loop  # noqa: E402
from r4r_ring_agent.worktrees import WorktreePaths  # noqa: E402


if __name__ == "__main__":
    paths = WorktreePaths(RING_WORKTREE, PC_WORKTREE, LP_WORKTREE)
    raise SystemExit(run_ring_loop(paths, once=RUN_ONCE))
