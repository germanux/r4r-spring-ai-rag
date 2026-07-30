from pathlib import Path
import json
import tempfile
import unittest

from r4r_codex_agent.contracts import Task, TaskPlan
from r4r_codex_agent.runner import (
    AutomaticRunner,
    codex_exec_command,
    extract_opencode_text,
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

    def test_extracts_local_understanding_from_opencode_jsonl(self):
        stdout = "\n".join([
            json.dumps({"type": "step_start", "part": {"type": "step-start"}}),
            json.dumps({"type": "text", "part": {"type": "text", "text": "# Local understanding report"}}),
            json.dumps({"type": "text", "part": {"type": "text", "text": "## Task objective in my own words\nImplement ingestion."}}),
        ])

        report = extract_opencode_text(stdout)

        self.assertIn("# Local understanding report", report)
        self.assertIn("Implement ingestion", report)

    def test_instruction_bundle_includes_task_companion_and_codex_extra(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            (repo / "AGENTS.md").write_text("global", encoding="utf-8")
            commands = repo / ".opencode" / "commands"
            commands.mkdir(parents=True)
            (commands / "task.md").write_text("workflow", encoding="utf-8")
            task_path = commands / "task-02-ingestion.md"
            task_path.write_text("task", encoding="utf-8")
            guide = commands / "task-02-ingestion-implementation-guide.md"
            guide.write_text("guide", encoding="utf-8")
            unrelated = commands / "task-03-pgvector.md"
            unrelated.write_text("unrelated", encoding="utf-8")

            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            runner.control_dir = repo / "runtime" / "control"
            runner.control_dir.mkdir(parents=True)
            runner.codex_extra_instructions_path = (
                runner.control_dir / "codex-qwen3-extra-instructions.md"
            )
            runner.codex_extra_instructions_path.write_text("correction", encoding="utf-8")
            task = Task(
                "task-02-ingestion",
                ".opencode/commands/task-02-ingestion.md",
                "ingestion",
                ("src/**",),
                ("true",),
                "commit",
            )

            files = runner._instruction_files(task)
            relative = {str(path.relative_to(repo)) for path in files}

            self.assertIn("AGENTS.md", relative)
            self.assertIn(".opencode/commands/task.md", relative)
            self.assertIn(".opencode/commands/task-02-ingestion.md", relative)
            self.assertIn(
                ".opencode/commands/task-02-ingestion-implementation-guide.md",
                relative,
            )
            self.assertIn("runtime/control/codex-qwen3-extra-instructions.md", relative)
            self.assertNotIn(".opencode/commands/task-03-pgvector.md", relative)

    def test_codex_revision_writes_extra_instructions_and_accept_clears_them(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            runner.control_dir = repo / "runtime" / "control"
            runner.codex_extra_instructions_path = (
                runner.control_dir / "codex-qwen3-extra-instructions.md"
            )
            task = Task("task-02", "task.md", "objective", ("src/**",), ("true",), "commit")
            revise = {
                "decision": "REVISE",
                "next_action": "Fix the rollback test.",
                "local_understanding_assessment": "The worker bypassed the proxy.",
                "instruction_corrections": ["Use the Spring-managed service."],
                "corrected_extra_instructions": "Autowire the service and use a trigger.",
            }

            runner._write_codex_extra_instructions(task, revise)

            content = runner.codex_extra_instructions_path.read_text(encoding="utf-8")
            self.assertIn("The worker bypassed the proxy", content)
            self.assertIn("Use the Spring-managed service", content)
            self.assertIn("Autowire the service and use a trigger", content)

            runner._write_codex_extra_instructions(task, {"decision": "ACCEPT"})
            self.assertFalse(runner.codex_extra_instructions_path.exists())

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

    def test_resume_lock_auto_advances_across_maintenance_commit(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self._init_repo(repo)
            runner = self._selection_runner(repo)
            base = run_command(("git", "rev-parse", "HEAD"), repo).stdout.strip()
            runner.lock_path.parent.mkdir(parents=True)
            runner.lock_path.write_text(
                json.dumps({
                    "schema_version": 1,
                    "task_id": "task-02",
                    "base_commit": base,
                    "run_id": "test",
                    "allowed_paths": ["src/**"],
                }),
                encoding="utf-8",
            )
            script = repo / "scripts" / "run-codex-agent.sh"
            script.parent.mkdir(parents=True)
            script.write_text("#!/usr/bin/env bash\n", encoding="utf-8")
            command = repo / ".opencode" / "commands" / "recovery.md"
            command.parent.mkdir(parents=True)
            command.write_text("# Recovery\n", encoding="utf-8")
            run_command((
                "git", "add",
                str(script.relative_to(repo)),
                str(command.relative_to(repo)),
            ), repo)
            run_command(("git", "commit", "-q", "-m", "agent maintenance"), repo)
            current = run_command(("git", "rev-parse", "HEAD"), repo).stdout.strip()

            runner._validate_resume_lock(())

            lock = json.loads(runner.lock_path.read_text(encoding="utf-8"))
            self.assertEqual(current, lock["base_commit"])
            self.assertTrue(lock["run_id"].startswith("resume-"))

    def test_resume_lock_rejects_product_commit_between_base_and_head(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self._init_repo(repo)
            runner = self._selection_runner(repo)
            base = run_command(("git", "rev-parse", "HEAD"), repo).stdout.strip()
            runner.lock_path.parent.mkdir(parents=True)
            runner.lock_path.write_text(
                json.dumps({
                    "schema_version": 1,
                    "task_id": "task-02",
                    "base_commit": base,
                    "run_id": "test",
                    "allowed_paths": ["src/**"],
                }),
                encoding="utf-8",
            )
            product = repo / "src" / "main" / "App.java"
            product.parent.mkdir(parents=True)
            product.write_text("class App {}\n", encoding="utf-8")
            run_command(("git", "add", str(product.relative_to(repo))), repo)
            run_command(("git", "commit", "-q", "-m", "product change"), repo)

            with self.assertRaisesRegex(RuntimeError, "non-maintenance commits"):
                runner._validate_resume_lock(())

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
