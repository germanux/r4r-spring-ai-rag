from pathlib import Path
import json
import tempfile
import unittest

from r4r_codex_agent.contracts import Task, TaskPlan
from r4r_codex_agent.runner import (
    AutomaticRunner,
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
            self._init_repo(repo)

            baseline = git_worktree_fingerprint(repo)
            (repo / "new.txt").write_text("first", encoding="utf-8")
            first = git_worktree_fingerprint(repo)
            (repo / "new.txt").write_text("second", encoding="utf-8")
            second = git_worktree_fingerprint(repo)

            self.assertNotEqual(baseline, first)
            self.assertNotEqual(first, second)

    def test_active_lock_is_authoritative_for_task_selection(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = self._selection_runner(Path(directory))
            runner.lock_path.parent.mkdir(parents=True)
            runner.lock_path.write_text(
                json.dumps({"schema_version": 1, "task_id": "task-02"}),
                encoding="utf-8",
            )

            selected = runner._select_task()

            self.assertIsNotNone(selected)
            self.assertEqual("task-02", selected.id)

    def test_without_lock_selects_first_non_accepted_task(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = self._selection_runner(Path(directory))

            selected = runner._select_task()

            self.assertIsNotNone(selected)
            self.assertEqual("task-02", selected.id)

    def test_patch_evidence_contains_untracked_file_content(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self._init_repo(repo)
            (repo / "new.txt").write_text("new evidence\n", encoding="utf-8")
            attempt = repo / "runtime" / "attempt-01"

            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            runner._write_patch(attempt, ("new.txt",))

            patch = (attempt / "evidence" / "changes.patch").read_text(encoding="utf-8")
            self.assertIn("new evidence", patch)
            self.assertIn("new file mode", patch)

    @staticmethod
    def _init_repo(repo: Path) -> None:
        run_command(("git", "init", "-q"), repo)
        run_command(("git", "config", "user.email", "test@example.invalid"), repo)
        run_command(("git", "config", "user.name", "Test Runner"), repo)
        (repo / "tracked.txt").write_text("baseline", encoding="utf-8")
        run_command(("git", "add", "tracked.txt"), repo)
        run_command(("git", "commit", "-q", "-m", "baseline"), repo)

    @staticmethod
    def _selection_runner(repo: Path) -> AutomaticRunner:
        task_01 = Task("task-01", "task-01.md", "base", ("src/**",), ("false",), "task 01")
        task_02 = Task("task-02", "task-02.md", "next", ("src/**",), ("true",), "task 02")
        runner = object.__new__(AutomaticRunner)
        runner.repo = repo
        runner.plan = TaskPlan(1, (task_01, task_02), ("true",))
        runner.progress = {
            "schema_version": 1,
            "active_task": None,
            "last_run": None,
            "tasks": [
                {"id": "task-01", "status": "ACCEPTED", "accepted_at": "now"},
                {"id": "task-02", "status": "PENDING", "accepted_at": None},
            ],
        }
        runner.lock_path = repo / "runtime" / "locks" / "active-task.json"
        return runner


if __name__ == "__main__":
    unittest.main()
