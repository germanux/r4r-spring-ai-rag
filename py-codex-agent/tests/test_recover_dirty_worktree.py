from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from r4r_codex_agent.recover_dirty_worktree import (
    RecoveryRefused,
    recover_dirty_worktree,
)
from r4r_codex_agent.runner import run_command


class RecoverDirtyWorktreeTest(unittest.TestCase):
    def test_adopts_allowed_changes_for_first_unaccepted_task(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            head = self._prepare_repo(repo)
            product = repo / "src" / "main" / "App.java"
            product.parent.mkdir(parents=True)
            product.write_text("class App {}\n", encoding="utf-8")
            state = self._write_failure_state(repo, head, ["src/main/App.java"])

            task, paths, _, created = recover_dirty_worktree(
                repo,
                Path(".opencode/task-plan.json"),
                Path(".opencode/progress.json"),
                state,
            )

            self.assertTrue(created)
            self.assertEqual("task-02", task.id)
            self.assertEqual(("src/main/App.java",), paths)
            lock = json.loads(
                (repo / "runtime/locks/active-task.json").read_text(encoding="utf-8")
            )
            self.assertEqual("task-02", lock["task_id"])
            self.assertEqual(head, lock["base_commit"])
            self.assertEqual(["src/**"], lock["allowed_paths"])

    def test_rejects_out_of_scope_change(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            head = self._prepare_repo(repo)
            script = repo / "scripts" / "unexpected.sh"
            script.parent.mkdir(parents=True)
            script.write_text("#!/bin/sh\n", encoding="utf-8")
            state = self._write_failure_state(repo, head, ["scripts/unexpected.sh"])

            with self.assertRaisesRegex(RecoveryRefused, "do not belong exclusively"):
                recover_dirty_worktree(
                    repo,
                    Path(".opencode/task-plan.json"),
                    Path(".opencode/progress.json"),
                    state,
                )

            self.assertFalse((repo / "runtime/locks/active-task.json").exists())

    def test_rejects_worktree_drift_after_failure_state(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            head = self._prepare_repo(repo)
            first = repo / "src" / "main" / "App.java"
            first.parent.mkdir(parents=True)
            first.write_text("class App {}\n", encoding="utf-8")
            state = self._write_failure_state(repo, head, ["src/main/App.java"])
            second = repo / "src" / "test" / "AppTest.java"
            second.parent.mkdir(parents=True)
            second.write_text("class AppTest {}\n", encoding="utf-8")

            with self.assertRaisesRegex(RecoveryRefused, "worktree changed"):
                recover_dirty_worktree(
                    repo,
                    Path(".opencode/task-plan.json"),
                    Path(".opencode/progress.json"),
                    state,
                )

    def test_valid_existing_lock_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            head = self._prepare_repo(repo)
            product = repo / "src" / "main" / "App.java"
            product.parent.mkdir(parents=True)
            product.write_text("class App {}\n", encoding="utf-8")
            state = self._write_failure_state(repo, head, ["src/main/App.java"])
            lock_path = repo / "runtime" / "locks" / "active-task.json"
            lock_path.parent.mkdir(parents=True)
            lock_path.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "task_id": "task-02",
                        "base_commit": head,
                        "run_id": "existing",
                        "allowed_paths": ["src/**"],
                    }
                ),
                encoding="utf-8",
            )

            task, paths, _, created = recover_dirty_worktree(
                repo,
                Path(".opencode/task-plan.json"),
                Path(".opencode/progress.json"),
                state,
            )

            self.assertFalse(created)
            self.assertEqual("task-02", task.id)
            self.assertEqual(("src/main/App.java",), paths)

    @staticmethod
    def _prepare_repo(repo: Path) -> str:
        run_command(("git", "init", "-q"), repo)
        run_command(("git", "config", "user.email", "test@example.invalid"), repo)
        run_command(("git", "config", "user.name", "Test Runner"), repo)
        (repo / "tracked.txt").write_text("baseline\n", encoding="utf-8")

        opencode = repo / ".opencode"
        opencode.mkdir()
        (opencode / "task-plan.json").write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "tasks": [
                        {
                            "id": "task-01",
                            "command": "task-01.md",
                            "objective": "base",
                            "allowed_paths": ["src/**"],
                            "gate": ["true"],
                            "commit_message": "task 01",
                        },
                        {
                            "id": "task-02",
                            "command": "task-02.md",
                            "objective": "next",
                            "allowed_paths": ["src/**"],
                            "gate": ["true"],
                            "commit_message": "task 02",
                        },
                    ],
                    "final_gate": ["true"],
                }
            ),
            encoding="utf-8",
        )
        (opencode / "progress.json").write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "active_task": None,
                    "last_run": None,
                    "tasks": [
                        {"id": "task-01", "status": "ACCEPTED", "accepted_at": "now"},
                        {"id": "task-02", "status": "PENDING", "accepted_at": None},
                    ],
                }
            ),
            encoding="utf-8",
        )
        run_command(("git", "add", "tracked.txt", ".opencode/task-plan.json", ".opencode/progress.json"), repo)
        run_command(("git", "commit", "-q", "-m", "baseline"), repo)
        return run_command(("git", "rev-parse", "HEAD"), repo).stdout.strip()

    @staticmethod
    def _write_failure_state(repo: Path, head: str, paths: list[str]) -> Path:
        state_path = repo / "runtime" / "runs" / "20260730T220000Z" / "state.json"
        state_path.parent.mkdir(parents=True)
        state_path.write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "run_id": "20260730T220000Z",
                    "status": "DIRTY_WORKTREE_UNOWNED",
                    "exit_code": 64,
                    "git_head": head,
                    "changed_paths": paths,
                }
            ),
            encoding="utf-8",
        )
        return state_path


if __name__ == "__main__":
    unittest.main()
