from pathlib import Path
import tempfile
import unittest

from r4r_codex_agent.runner import (
    codex_exec_command,
    git_worktree_fingerprint,
    path_is_allowed,
    run_command,
)


class RunnerTest(unittest.TestCase):
    def test_runs_without_shell_and_preserves_exit(self):
        with tempfile.TemporaryDirectory() as directory:
            result = run_command(("python3", "-c", "raise SystemExit(7)"), Path(directory))
        self.assertEqual(7, result.exit_code)

    def test_matches_allowed_paths(self):
        self.assertTrue(path_is_allowed("src/main/App.java", ("src/**",)))
        self.assertFalse(path_is_allowed("scripts/task-gate.sh", ("src/**",)))

    def test_builds_read_only_codex_command(self):
        command = codex_exec_command("codex", Path("schema.json"), Path("out.json"), "gpt-test")
        self.assertIn("read-only", command)
        self.assertIn("--output-schema", command)
        self.assertEqual("-", command[-1])

    def test_worktree_fingerprint_detects_untracked_content_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            run_command(("git", "init", "-q"), repo)
            run_command(("git", "config", "user.email", "test@example.invalid"), repo)
            run_command(("git", "config", "user.name", "Test Runner"), repo)
            (repo / "tracked.txt").write_text("baseline", encoding="utf-8")
            run_command(("git", "add", "tracked.txt"), repo)
            run_command(("git", "commit", "-q", "-m", "baseline"), repo)

            baseline = git_worktree_fingerprint(repo)
            (repo / "new.txt").write_text("first", encoding="utf-8")
            first = git_worktree_fingerprint(repo)
            (repo / "new.txt").write_text("second", encoding="utf-8")
            second = git_worktree_fingerprint(repo)

            self.assertNotEqual(baseline, first)
            self.assertNotEqual(first, second)


if __name__ == "__main__":
    unittest.main()
