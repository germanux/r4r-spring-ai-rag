from __future__ import annotations

from pathlib import Path
import subprocess
import tempfile
import unittest

from r4r_ring_agent.worktrees import (
    current_branch,
    find_repository_anchor,
    is_git_worktree,
    move_or_create_worker,
    repair_registered_worktrees,
)


def git(path: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", "-C", str(path), *args],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=True,
    )


class WorktreeTests(unittest.TestCase):
    def make_repository(self, root: Path) -> tuple[Path, Path, Path]:
        main = root / "old-main.git"
        subprocess.run(["git", "init", str(main)], check=True, stdout=subprocess.DEVNULL)
        git(main, "config", "user.email", "tests@example.invalid")
        git(main, "config", "user.name", "Ring Tests")
        (main / "README.md").write_text("base\n", encoding="utf-8")
        git(main, "add", "README.md")
        git(main, "commit", "-m", "base")
        git(main, "branch", "agent/ring-agent-worker")
        git(main, "branch", "agent/laptop-qwen3-worker")

        ring = root / "ring.git"
        lp = root / "old-lp.git"
        git(main, "worktree", "add", str(ring), "agent/ring-agent-worker")
        git(main, "worktree", "add", str(lp), "agent/laptop-qwen3-worker")
        return main, ring, lp

    def test_repairs_linked_worktrees_after_primary_checkout_rename(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            old_main, ring, lp = self.make_repository(root)
            new_main = root / "pc.git"

            old_main.rename(new_main)
            self.assertTrue(is_git_worktree(new_main))
            self.assertFalse(is_git_worktree(ring))
            self.assertFalse(is_git_worktree(lp))

            anchor = find_repository_anchor((new_main, ring))
            repair_registered_worktrees(anchor)

            self.assertTrue(is_git_worktree(ring))
            self.assertTrue(is_git_worktree(lp))

    def test_moves_linked_worker_after_primary_checkout_rename(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            old_main, ring, old_lp = self.make_repository(root)
            new_main = root / "pc.git"
            new_lp = root / "lp.git"

            old_main.rename(new_main)
            repair_registered_worktrees(new_main)
            message = move_or_create_worker(
                repository_anchor=new_main,
                source=old_lp,
                destination=new_lp,
                branch="agent/laptop-qwen3-worker",
            )

            self.assertIn("moved:", message)
            self.assertFalse(old_lp.exists())
            self.assertTrue(is_git_worktree(new_lp))
            self.assertEqual(current_branch(new_lp), "agent/laptop-qwen3-worker")
            self.assertTrue(is_git_worktree(ring))

    def test_primary_move_repairs_linked_worktrees_in_same_operation(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            old_main, ring, lp = self.make_repository(root)
            new_main = root / "pc.git"

            message = move_or_create_worker(
                repository_anchor=old_main,
                source=old_main,
                destination=new_main,
                branch=current_branch(old_main),
            )

            self.assertIn("moved:", message)
            self.assertTrue(is_git_worktree(new_main))
            self.assertTrue(is_git_worktree(ring))
            self.assertTrue(is_git_worktree(lp))


if __name__ == "__main__":
    unittest.main()
