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

from r4r_ring_agent.worktrees import (  # noqa: E402
    find_repository_anchor,
    move_or_create_worker,
    repair_registered_worktrees,
)


def main() -> int:
    # Recovery-first: after an interrupted older version, the primary checkout
    # may already be renamed while every linked worktree still points to the old
    # common .git directory. The new PC path is therefore checked first.
    anchor = find_repository_anchor(
        (PC_WORKTREE, OLD_PC_WORKTREE, RING_WORKTREE, OLD_LP_WORKTREE, LP_WORKTREE)
    )
    repair_registered_worktrees(anchor)

    print(
        move_or_create_worker(
            repository_anchor=anchor,
            source=OLD_PC_WORKTREE,
            destination=PC_WORKTREE,
            branch=PC_BRANCH,
        )
    )

    # PC_WORKTREE is now the stable repository anchor, including when it was the
    # primary checkout renamed by this run.
    anchor = find_repository_anchor((PC_WORKTREE, RING_WORKTREE))
    repair_registered_worktrees(anchor)

    print(
        move_or_create_worker(
            repository_anchor=anchor,
            source=OLD_LP_WORKTREE,
            destination=LP_WORKTREE,
            branch=LP_BRANCH,
        )
    )
    repair_registered_worktrees(anchor)

    print("Worker paths are ready.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
