from __future__ import annotations

from pathlib import Path
import subprocess
import tempfile
import unittest

from r4r_ring_agent.worktrees import (
    WorktreePaths,
    current_branch,
    move_or_create_worker,
    require_git_worktree,
)


def git(repo: Path, *args: str) -> None:
    subprocess.run(
        ["git", "-C", str(repo), *args],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )


class WorktreesTest(unittest.TestCase):
    def make_repo(self, root: Path) -> Path:
        repo = root / "ring.git"
        repo.mkdir()
        git(repo, "init")
        git(repo, "config", "user.name", "Test")
        git(repo, "config", "user.email", "test@example.invalid")
        (repo / "README.md").write_text("base\n", encoding="utf-8")
        git(repo, "add", ".")
        git(repo, "commit", "-m", "base")
        git(repo, "branch", "agent/pc-qwen3-worker")
        return repo

    def test_paths_select_worker(self) -> None:
        paths = WorktreePaths(Path("ring"), Path("pc"), Path("lp"))
        self.assertEqual(paths.worker("pc"), Path("pc"))
        self.assertEqual(paths.worker("LP"), Path("lp"))
        with self.assertRaises(ValueError):
            paths.worker("bad")

    def test_existing_linked_worktree_is_moved_to_short_name(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            ring = self.make_repo(root)
            old = root / "r4r-spring-ai-rag.git"
            new = root / "r4r-pc-worker.git"
            git(ring, "worktree", "add", str(old), "agent/pc-qwen3-worker")

            result = move_or_create_worker(
                ring_repo=ring,
                source=old,
                destination=new,
                branch="agent/pc-qwen3-worker",
            )

            self.assertIn("moved:", result)
            self.assertFalse(old.exists())
            self.assertEqual(require_git_worktree(new, "PC"), new.resolve())
            self.assertEqual(current_branch(new), "agent/pc-qwen3-worker")


if __name__ == "__main__":
    unittest.main()
