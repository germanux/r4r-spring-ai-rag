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
RING_BRANCH = "agent/ring-agent-worker"

OLD_PC_WORKTREE = DEVELOPMENT_ROOT / "r4r-spring-ai-rag.git"
OLD_LP_WORKTREE = DEVELOPMENT_ROOT / "r4r-spring-ai-rag-laptop-agent.git"

PC_WORKTREE = DEVELOPMENT_ROOT / "r4r-pc-worker.git"
LP_WORKTREE = DEVELOPMENT_ROOT / "r4r-lp-worker.git"

PC_BRANCH = "agent/pc-qwen3-worker"
LP_BRANCH = "agent/laptop-qwen3-worker"

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "src"))

from r4r_ring_agent.worktrees import (  # noqa: E402
    current_branch,
    find_repository_anchor,
    is_git_worktree,
    move_or_create_worker,
    repair_registered_worktrees,
    require_git_worktree,
)


def require_expected_branch(path: Path, expected: str, label: str) -> None:
    worktree = require_git_worktree(path, label)
    actual = current_branch(worktree)
    if actual != expected:
        raise RuntimeError(
            f"{label} uses branch {actual or 'DETACHED'}; expected {expected}. "
            "This script never checks out or creates replacement branches."
        )



def select_source(old_path: Path, new_path: Path, expected_branch: str, label: str) -> Path:
    """Prefer an already-valid short-path worktree without deleting the old path.

    A previous interrupted migration can leave the legacy path recreated as a
    normal directory or stale administrative path. When the new path is already
    the valid worktree on the expected branch, that legacy path is ignored but
    never removed automatically. Two simultaneously valid Git worktrees remain
    an error because choosing one silently would be ambiguous.
    """
    if not new_path.exists():
        return old_path

    require_expected_branch(new_path, expected_branch, label)

    if old_path.exists() and old_path.resolve() != new_path.resolve():
        if is_git_worktree(old_path):
            raise RuntimeError(
                f"{label}: both legacy and short paths are valid Git worktrees: "
                f"{old_path} and {new_path}. Resolve this ambiguity manually."
            )
        print(
            f"warning: ignoring non-worktree legacy path for {label}: {old_path}; "
            "it was not deleted"
        )

    return new_path

def main() -> int:
    # Recovery-first: after an interrupted older version, the primary checkout
    # may already be renamed while every linked worktree still points to the old
    # common .git directory. The new PC path is therefore checked first.
    anchor = find_repository_anchor(
        (PC_WORKTREE, OLD_PC_WORKTREE, RING_WORKTREE, OLD_LP_WORKTREE, LP_WORKTREE)
    )
    repair_registered_worktrees(anchor)

    # The Ring worktree is already configured by the operator. Never switch it;
    # only verify that it is attached to the exact existing branch.
    require_expected_branch(RING_WORKTREE, RING_BRANCH, "RING")

    pc_source = select_source(OLD_PC_WORKTREE, PC_WORKTREE, PC_BRANCH, "PC")
    print(
        move_or_create_worker(
            repository_anchor=anchor,
            source=pc_source,
            destination=PC_WORKTREE,
            branch=PC_BRANCH,
        )
    )

    # PC_WORKTREE is now the stable repository anchor, including when it was the
    # primary checkout renamed by this run.
    anchor = find_repository_anchor((PC_WORKTREE, RING_WORKTREE))
    repair_registered_worktrees(anchor)

    lp_source = select_source(OLD_LP_WORKTREE, LP_WORKTREE, LP_BRANCH, "LP")
    print(
        move_or_create_worker(
            repository_anchor=anchor,
            source=lp_source,
            destination=LP_WORKTREE,
            branch=LP_BRANCH,
        )
    )
    repair_registered_worktrees(anchor)

    # Final invariant: the three persistent worktrees must remain attached to
    # the three pre-existing worker branches shown in SmartGit.
    require_expected_branch(RING_WORKTREE, RING_BRANCH, "RING")
    require_expected_branch(PC_WORKTREE, PC_BRANCH, "PC")
    require_expected_branch(LP_WORKTREE, LP_BRANCH, "LP")

    print("Worker paths are ready with the expected existing branches:")
    print(f"- RING: {RING_WORKTREE} [{RING_BRANCH}]")
    print(f"- PC:   {PC_WORKTREE} [{PC_BRANCH}]")
    print(f"- LP:   {LP_WORKTREE} [{LP_BRANCH}]")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
