#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

# ---------------------------------------------------------------------------
# EDIT THESE PATHS HERE. The maintainer only changes the Ring worktree.
# ---------------------------------------------------------------------------
DEVELOPMENT_ROOT = Path.home() / "Desarrollo"
RING_WORKTREE = DEVELOPMENT_ROOT / "r4r-ring-agent.git"
PC_WORKTREE = DEVELOPMENT_ROOT / "r4r-pc-worker.git"
LP_WORKTREE = DEVELOPMENT_ROOT / "r4r-lp-worker.git"
RUN_ONCE = False

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "src"))

from r4r_ring_agent.harness_maintainer import run_maintenance_loop  # noqa: E402
from r4r_ring_agent.worktrees import require_git_worktree  # noqa: E402


if __name__ == "__main__":
    ring_repo = require_git_worktree(RING_WORKTREE, "RING")
    raise SystemExit(run_maintenance_loop(ring_repo, once=RUN_ONCE or "--once" in sys.argv[1:]))
