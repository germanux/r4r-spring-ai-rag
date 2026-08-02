#!/usr/bin/env python3
from __future__ import annotations

import os
from pathlib import Path
import sys

# ---------------------------------------------------------------------------
# Environment-aware canonical worktrees. No command-line parameters required.
# ---------------------------------------------------------------------------
DEVELOPMENT_ROOT = Path(
    os.environ.get("R4R_DEVELOPMENT_ROOT", str(Path.home() / "Desarrollo"))
).expanduser()
RING_WORKTREE = Path(
    os.environ.get("R4R_RING_WORKTREE", str(DEVELOPMENT_ROOT / "r4r-ring-agent.git"))
).expanduser()
PC_WORKTREE = Path(
    os.environ.get("R4R_PC_WORKTREE", str(DEVELOPMENT_ROOT / "r4r-pc-worker.git"))
).expanduser()
LP_WORKTREE = Path(
    os.environ.get("R4R_LP_WORKTREE", str(DEVELOPMENT_ROOT / "r4r-lp-worker.git"))
).expanduser()
RUN_ONCE = os.environ.get("R4R_RING_RUN_ONCE", "false").lower() == "true"

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "src"))

from r4r_ring_agent.ring_loop import run_ring_loop  # noqa: E402
from r4r_ring_agent.worktrees import WorktreePaths  # noqa: E402


if __name__ == "__main__":
    paths = WorktreePaths(RING_WORKTREE, PC_WORKTREE, LP_WORKTREE)
    raise SystemExit(run_ring_loop(paths, once=RUN_ONCE))
