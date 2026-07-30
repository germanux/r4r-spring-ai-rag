from pathlib import Path
import json
import tempfile
import unittest

from r4r_codex_agent.repair_active_task_lock import (
    RepairRefused,
    repair_active_task_lock,
)
from r4r_codex_agent.runner import run_command


class RepairActiveTaskLockTest(unittest.TestCase):
    def _git(self, repo: Path, *args: str) -> str:
        result = run_command(("git", *args), repo)
        self.assertEqual(0, result.exit_code, result.stderr)
        return result.stdout.strip()

    def _init_repo(self, repo: Path) -> str:
        self._git(repo, "init", "-q")
        self._git(repo, "config", "user.name", "Test")
        self._git(repo, "config", "user.email", "test@example.invalid")
        (repo / ".opencode").mkdir()
        (repo / ".opencode" / "task-plan.json").write_text(json.dumps({
            "schema_version": 1,
            "final_gate": ["true"],
            "tasks": [{
                "id": "task-03-pgvector",
                "command": ".opencode/commands/task-03.md",
                "objective": "pgvector",
                "allowed_paths": ["src/main/**", "src/test/**"],
                "gate": ["true"],
                "commit_message": "task03",
            }],
        }), encoding="utf-8")
        (repo / ".opencode" / "progress.json").write_text(json.dumps({
            "schema_version": 1,
            "active_task": None,
            "last_run": None,
            "tasks": [{
                "id": "task-03-pgvector",
                "status": "PENDING",
                "accepted_at": None,
            }],
        }), encoding="utf-8")
        (repo / ".opencode" / "commands").mkdir()
        (repo / ".opencode" / "commands" / "task-03.md").write_text("task", encoding="utf-8")
        self._git(repo, "add", ".")
        self._git(repo, "commit", "-qm", "base")
        return self._git(repo, "rev-parse", "HEAD")

    def test_repairs_lock_across_packaged_maintenance_commit(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            base = self._init_repo(repo)
            lock_path = repo / "runtime" / "locks" / "active-task.json"
            lock_path.parent.mkdir(parents=True)
            lock_path.write_text(json.dumps({
                "schema_version": 1,
                "task_id": "task-03-pgvector",
                "base_commit": base,
                "run_id": "old",
                "allowed_paths": ["src/main/**", "src/test/**"],
            }), encoding="utf-8")

            packaged = repo / "r4r-agent-update-v2" / "py-codex-agent" / "src" / "r4r_codex_agent"
            packaged.mkdir(parents=True)
            (packaged / "runner.py").write_text("maintenance", encoding="utf-8")
            self._git(
                repo,
                "add",
                "r4r-agent-update-v2/py-codex-agent/src/r4r_codex_agent/runner.py",
            )
            self._git(repo, "commit", "-qm", "maintenance package")
            head = self._git(repo, "rev-parse", "HEAD")

            product = repo / "src" / "main" / "App.java"
            product.parent.mkdir(parents=True)
            product.write_text("class App {}", encoding="utf-8")

            state_dir = repo / "runtime" / "runs" / "20260731T000000Z"
            state_dir.mkdir(parents=True)
            state = state_dir / "state.json"
            state.write_text(json.dumps({
                "schema_version": 1,
                "status": "CONTROLLER_EXCEPTION",
                "exit_code": 2,
                "git_head": head,
                "error": "Active-task lock cannot advance across non-maintenance commits: ['r4r-agent-update-v2/py-codex-agent/src/r4r_codex_agent/runner.py']",
            }), encoding="utf-8")

            old, new, dirty, _ = repair_active_task_lock(
                repo,
                Path(".opencode/task-plan.json"),
                Path(".opencode/progress.json"),
                state,
            )

            self.assertEqual(base, old)
            self.assertEqual(head, new)
            self.assertEqual(("src/main/App.java",), dirty)
            repaired = json.loads(lock_path.read_text(encoding="utf-8"))
            self.assertEqual(head, repaired["base_commit"])

    def test_refuses_real_product_commit_between_lock_and_head(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            base = self._init_repo(repo)
            lock_path = repo / "runtime" / "locks" / "active-task.json"
            lock_path.parent.mkdir(parents=True)
            lock_path.write_text(json.dumps({
                "schema_version": 1,
                "task_id": "task-03-pgvector",
                "base_commit": base,
                "run_id": "old",
                "allowed_paths": ["src/main/**", "src/test/**"],
            }), encoding="utf-8")

            product = repo / "src" / "main" / "Committed.java"
            product.parent.mkdir(parents=True)
            product.write_text("class Committed {}", encoding="utf-8")
            self._git(repo, "add", ".")
            self._git(repo, "commit", "-qm", "product")
            head = self._git(repo, "rev-parse", "HEAD")
            dirty = repo / "src" / "test" / "Pending.java"
            dirty.parent.mkdir(parents=True)
            dirty.write_text("class Pending {}", encoding="utf-8")

            state_dir = repo / "runtime" / "runs" / "20260731T000000Z"
            state_dir.mkdir(parents=True)
            state = state_dir / "state.json"
            state.write_text(json.dumps({
                "schema_version": 1,
                "status": "CONTROLLER_EXCEPTION",
                "exit_code": 2,
                "git_head": head,
                "error": "Active-task lock cannot advance across non-maintenance commits: ['src/main/Committed.java']",
            }), encoding="utf-8")

            with self.assertRaisesRegex(RepairRefused, "real non-maintenance"):
                repair_active_task_lock(
                    repo,
                    Path(".opencode/task-plan.json"),
                    Path(".opencode/progress.json"),
                    state,
                )


if __name__ == "__main__":
    unittest.main()
