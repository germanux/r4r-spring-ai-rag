from __future__ import annotations

from pathlib import Path
import subprocess
import tempfile
import unittest

from r4r_ring_agent.maintenance_policy import (
    detached_worktree,
    inspect_candidate,
)


def git(repo: Path, *args: str) -> None:
    subprocess.run(["git", *args], cwd=repo, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)


class MaintenancePolicyTest(unittest.TestCase):
    def make_repo(self, root: Path) -> Path:
        git(root, "init")
        git(root, "config", "user.name", "Test")
        git(root, "config", "user.email", "test@example.invalid")
        (root / "py-ring-agent").mkdir()
        (root / "py-ring-agent" / "a.py").write_text("VALUE = 1\n")
        git(root, "add", ".")
        git(root, "commit", "-m", "base")
        return root

    def test_budget_and_scope_are_enforced(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo = self.make_repo(Path(temporary))
            (repo / "py-ring-agent" / "a.py").write_text("VALUE = 2\n")
            candidate = inspect_candidate(
                repo,
                allowed_globs=("py-ring-agent/**",),
                max_files=3,
                max_changed_lines=120,
            )
            self.assertTrue(candidate.valid)
            self.assertEqual(candidate.paths, ("py-ring-agent/a.py",))

            (repo / "outside.txt").write_text("bad\n")
            candidate = inspect_candidate(
                repo,
                allowed_globs=("py-ring-agent/**",),
                max_files=3,
                max_changed_lines=120,
            )
            self.assertFalse(candidate.valid)
            self.assertTrue(any("outside scope" in item for item in candidate.violations))

    def test_detached_worktree_is_removed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo = self.make_repo(Path(temporary))
            with detached_worktree(repo, "unit-test-worktree") as worktree:
                self.assertTrue(worktree.exists())
                self.assertTrue((worktree / "py-ring-agent" / "a.py").exists())
            self.assertFalse(worktree.exists())


if __name__ == "__main__":
    unittest.main()
