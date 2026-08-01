#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

# ---------------------------------------------------------------------------
# EDIT ONLY THIS BLOCK IF YOUR folders or branches differ.
# The script preserves the current worktrees; it does not clone product code.
# ---------------------------------------------------------------------------
DEVELOPMENT_ROOT = Path.home() / "Desarrollo"
RING_WORKTREE = DEVELOPMENT_ROOT / "r4r-ring-agent.git"

OLD_PC_WORKTREE = DEVELOPMENT_ROOT / "r4r-spring-ai-rag.git"
OLD_LP_WORKTREE = DEVELOPMENT_ROOT / "r4r-spring-ai-rag-laptop-agent.git"

PC_WORKTREE = DEVELOPMENT_ROOT / "r4r-pc-worker.git"
LP_WORKTREE = DEVELOPMENT_ROOT / "r4r-lp-worker.git"

PC_BRANCH = "agent/pc-qwen3-worker"
LP_BRANCH = "agent/laptop-qwen3-worker"

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "src"))

from r4r_ring_agent.worktrees import move_or_create_worker  # noqa: E402


def main() -> int:
    print(
        move_or_create_worker(
            ring_repo=RING_WORKTREE,
            source=OLD_PC_WORKTREE,
            destination=PC_WORKTREE,
            branch=PC_BRANCH,
        )
    )
    print(
        move_or_create_worker(
            ring_repo=RING_WORKTREE,
            source=OLD_LP_WORKTREE,
            destination=LP_WORKTREE,
            branch=LP_BRANCH,
        )
    )
    print("Worker paths are ready.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
