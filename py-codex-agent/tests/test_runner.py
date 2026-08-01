from pathlib import Path
import json
import os
import tempfile
import unittest
from unittest.mock import patch

from r4r_codex_agent.contracts import Task, TaskPlan
from r4r_codex_agent.diagnostics import GateDiagnostics
from r4r_codex_agent.runner import (
    AutomaticRunner,
    CommandResult,
    codex_exec_command,
    extract_codegraph_tool_calls,
    extract_opencode_text,
    git_product_changed_paths,
    git_worktree_fingerprint,
    is_controller_runtime_path,
    is_lock_auto_advance_path,
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

    def test_lock_auto_advance_accepts_canonical_and_packaged_maintenance_paths(self):
        self.assertTrue(
            is_lock_auto_advance_path(
                "py-codex-agent/src/r4r_codex_agent/runner.py"
            )
        )
        self.assertTrue(
            is_lock_auto_advance_path(
                "r4r-agent-update-v2/py-codex-agent/src/r4r_codex_agent/runner.py"
            )
        )
        self.assertTrue(
            is_lock_auto_advance_path(
                "r4r-self-recovery/scripts/run-codex-agent.sh"
            )
        )
        self.assertTrue(
            is_lock_auto_advance_path(
                "r4r-dual-agent-code-intelligence-v1/payload/scripts/cgr.sh"
            )
        )
        self.assertTrue(is_lock_auto_advance_path("README-DUAL-AGENTS.md"))
        self.assertTrue(
            is_lock_auto_advance_path("docs/dual-agent-code-intelligence.md")
        )
        self.assertTrue(is_lock_auto_advance_path("r4r-agent-update-v2.zip"))
        self.assertTrue(
            is_lock_auto_advance_path("install-r4r-agent-hotfix-v3.sh")
        )

    def test_lock_auto_advance_rejects_product_files_inside_bundle_directory(self):
        self.assertFalse(
            is_lock_auto_advance_path(
                "r4r-agent-update-v2/src/main/java/example/App.java"
            )
        )
        self.assertFalse(is_lock_auto_advance_path("todos.zip"))

    def test_controller_runtime_paths_are_not_product_scope(self):
        self.assertTrue(
            is_controller_runtime_path(
                "runtime/control/codex-qwen3-extra-instructions.md"
            )
        )
        self.assertFalse(is_controller_runtime_path("src/main/App.java"))

        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self._init_repo(repo)
            control = repo / "runtime" / "control"
            control.mkdir(parents=True)
            (control / "codex-qwen3-extra-instructions.md").write_text(
                "controller state",
                encoding="utf-8",
            )
            product = repo / "src" / "main" / "App.java"
            product.parent.mkdir(parents=True)
            product.write_text("class App {}\n", encoding="utf-8")

            changed = git_product_changed_paths(repo)

            self.assertEqual(("src/main/App.java",), changed)

    def test_fingerprint_ignores_peer_owned_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self._init_repo(repo)
            backend = repo / "src" / "main" / "App.java"
            frontend = repo / "frontend" / "src" / "app.ts"
            backend.parent.mkdir(parents=True, exist_ok=True)
            frontend.parent.mkdir(parents=True, exist_ok=True)
            backend.write_text("class App {}\n", encoding="utf-8")
            frontend.write_text("export const x = 1;\n", encoding="utf-8")
            before = git_worktree_fingerprint(repo, ("frontend/**",))
            frontend.write_text("export const x = 2;\n", encoding="utf-8")
            self.assertEqual(before, git_worktree_fingerprint(repo, ("frontend/**",)))
            backend.write_text("class App { int x; }\n", encoding="utf-8")
            self.assertNotEqual(before, git_worktree_fingerprint(repo, ("frontend/**",)))

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

    def test_resume_uses_pending_codex_extra_instructions_for_same_task(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            runner.control_dir = repo / "runtime" / "control"
            runner.control_dir.mkdir(parents=True)
            runner.codex_extra_instructions_path = (
                runner.control_dir / "codex-qwen3-extra-instructions.md"
            )
            runner.codex_extra_instructions_path.write_text(
                "- Active task: `task-02`\n\nFix exact headings.\n",
                encoding="utf-8",
            )
            task = Task("task-02", "task.md", "objective", ("src/**",), ("true",), "commit")
            other = Task("task-03", "task.md", "objective", ("src/**",), ("true",), "commit")

            self.assertIsNotNone(runner._resume_action_from_codex_extra(task))
            self.assertIsNone(runner._resume_action_from_codex_extra(other))

    def test_opencode_prompt_embeds_full_codex_extra_instructions(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            (repo / "AGENTS.md").write_text("global", encoding="utf-8")
            commands = repo / ".opencode" / "commands"
            commands.mkdir(parents=True)
            (commands / "task.md").write_text("workflow", encoding="utf-8")
            (commands / "task-02.md").write_text("task", encoding="utf-8")

            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            runner.control_dir = repo / "runtime" / "control"
            runner.control_dir.mkdir(parents=True)
            runner.codex_extra_instructions_path = (
                runner.control_dir / "codex-qwen3-extra-instructions.md"
            )
            runner.codex_extra_instructions_path.write_text(
                "Assert exact ordered heading paths.",
                encoding="utf-8",
            )
            task = Task(
                "task-02",
                ".opencode/commands/task-02.md",
                "ingestion",
                ("src/**",),
                ("./scripts/task-gate.sh", "task-02"),
                "commit",
            )
            gate = CommandResult(task.gate, 0, "green", "")

            diagnostics = GateDiagnostics(
                classification="compilation",
                summary="Java compilation failed.",
                fingerprint="abc",
                source_paths=("src/main/java/example/KnowledgeService.java",),
                related_paths=("src/main/java/example/KnowledgeService.java",),
                log_path="runtime/log",
                summary_path="runtime/summary",
                manifest_path="runtime/manifest",
                bundle_path="runtime/bundle.zip",
            )
            prompt = runner._opencode_prompt(
                task,
                gate,
                "Fix tests.",
                "# CodeGraph reconnaissance report\nFound KnowledgeService callers.",
                diagnostics,
            )

            self.assertIn("Assert exact ordered heading paths.", prompt)
            self.assertIn(
                "./scripts/task-gate.sh task-02",
                prompt,
            )
            self.assertIn("Do not add a pipeline", prompt)
            self.assertIn("FOCUSED CODEGRAPH EVIDENCE", prompt)
            self.assertIn("Found KnowledgeService callers", prompt)

    def test_extracts_actual_codegraph_tool_calls_from_nested_jsonl(self):
        stdout = "\n".join([
            json.dumps({
                "type": "tool",
                "part": {
                    "type": "tool",
                    "tool": "codegraph_search",
                    "state": {"status": "completed"},
                },
            }),
            json.dumps({
                "type": "text",
                "part": {
                    "type": "text",
                    "text": "I mention codegraph_fake in prose only.",
                },
            }),
        ])

        calls = extract_codegraph_tool_calls(stdout)

        self.assertEqual(("codegraph_search",), calls)

    def test_codegraph_prompt_requires_verified_mcp_call(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self._init_repo(repo)
            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            task = Task(
                "task-03",
                "task-03.md",
                "pgvector",
                ("src/**",),
                ("true",),
                "commit",
            )
            gate = CommandResult(task.gate, 1, "", "compile failure")

            prompt = runner._codegraph_reconnaissance_prompt(
                task, gate, 2, ("src/test/java/example/PgVectorKnowledgeStoreIT.java",)
            )

            self.assertIn("codegraph_", prompt)
            self.assertIn("failing source files", prompt)
            self.assertIn("prose about CodeGraph does not count", prompt)

    def test_codegraph_reconnaissance_persists_verified_tool_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self._init_repo(repo)
            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            runner.codegraph_bin = "codegraph"
            runner.opencode_bin = "opencode"
            runner.opencode_agent = "r4r-pc"
            runner.codegraph_retries = 0
            task = Task(
                "task-03",
                "task-03.md",
                "pgvector",
                ("src/**",),
                ("true",),
                "commit",
            )
            gate = CommandResult(task.gate, 1, "", "red")
            attempt = repo / "runtime" / "attempt-01"
            results = iter((
                CommandResult(("codegraph", "sync"), 0, "", ""),
                CommandResult(
                    ("opencode",),
                    0,
                    "\n".join((
                        json.dumps({
                            "type": "tool",
                            "part": {
                                "type": "tool",
                                "tool": "codegraph_search",
                            },
                        }),
                        json.dumps({
                            "type": "text",
                            "part": {
                                "type": "text",
                                "text": "# CodeGraph reconnaissance report\nFound PgVectorKnowledgeStore.",
                            },
                        }),
                    )),
                    "",
                ),
            ))
            runner._run_logged = lambda *args, **kwargs: next(results)

            report = runner._run_codegraph_reconnaissance(
                task,
                gate,
                attempt,
                ("src/test/java/example/PgVectorKnowledgeStoreIT.java",),
            )

            self.assertIn("PgVectorKnowledgeStore", report)
            calls = json.loads(
                (attempt / "evidence" / "codegraph-tool-calls.json").read_text(
                    encoding="utf-8"
                )
            )
            self.assertEqual(["codegraph_search"], calls["calls"])
            self.assertTrue(calls["required"])

    def test_codegraph_reconnaissance_rejects_prose_only_claim(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self._init_repo(repo)
            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            runner.codegraph_bin = "codegraph"
            runner.opencode_bin = "opencode"
            runner.opencode_agent = "r4r-pc"
            runner.codegraph_retries = 0
            task = Task(
                "task-03",
                "task-03.md",
                "pgvector",
                ("src/**",),
                ("true",),
                "commit",
            )
            gate = CommandResult(task.gate, 1, "", "red")
            results = iter((
                CommandResult(("codegraph", "sync"), 0, "", ""),
                CommandResult(
                    ("opencode",),
                    0,
                    json.dumps({
                        "type": "text",
                        "part": {
                            "type": "text",
                            "text": "I looked at CodeGraph without calling a tool.",
                        },
                    }),
                    "",
                ),
            ))
            runner._run_logged = lambda *args, **kwargs: next(results)

            with self.assertRaisesRegex(RuntimeError, "no actual codegraph"):
                runner._run_codegraph_reconnaissance(
                    task,
                    gate,
                    repo / "runtime" / "attempt-01",
                    ("src/test/java/example/PgVectorKnowledgeStoreIT.java",),
                )

    def test_record_unhandled_failure_writes_state(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self._init_repo(repo)
            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            runner.run_id = "20260730T235900Z"
            runner.run_dir = repo / "runtime" / "runs" / runner.run_id
            runner.run_dir.mkdir(parents=True)

            exit_code = runner.record_unhandled_failure(
                RuntimeError("out-of-scope maintenance files")
            )

            self.assertEqual(2, exit_code)
            state = json.loads(
                (runner.run_dir / "state.json").read_text(encoding="utf-8")
            )
            self.assertEqual("CONTROLLER_EXCEPTION", state["status"])
            self.assertEqual("RuntimeError", state["error_type"])
            self.assertIn("out-of-scope", state["error"])

    def test_compact_revision_context_omits_long_companion_but_keeps_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            (repo / "AGENTS.md").write_text("global", encoding="utf-8")
            commands = repo / ".opencode" / "commands"
            commands.mkdir(parents=True)
            (commands / "task.md").write_text("workflow", encoding="utf-8")
            task_path = commands / "task-02-ingestion.md"
            task_path.write_text("task", encoding="utf-8")
            guide = commands / "task-02-ingestion-implementation-guide.md"
            guide.write_text("very long guide\n", encoding="utf-8")

            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            runner.compact_revision_context = True
            runner.control_dir = repo / "runtime" / "control"
            runner.control_dir.mkdir(parents=True)
            runner.codex_extra_instructions_path = (
                runner.control_dir / "codex-qwen3-extra-instructions.md"
            )
            runner.codex_extra_instructions_path.write_text(
                "focused correction",
                encoding="utf-8",
            )
            task = Task(
                "task-02-ingestion",
                ".opencode/commands/task-02-ingestion.md",
                "ingestion",
                ("src/**",),
                ("true",),
                "commit",
            )

            files = runner._instruction_files(
                task,
                include_companion=runner._use_full_instruction_bundle("review"),
            )
            relative = {str(path.relative_to(repo)) for path in files}
            manifest = runner._instruction_manifest(task)

            self.assertNotIn(
                ".opencode/commands/task-02-ingestion-implementation-guide.md",
                relative,
            )
            self.assertIn(
                "runtime/control/codex-qwen3-extra-instructions.md",
                relative,
            )
            self.assertIn("task-02-ingestion-implementation-guide.md", manifest)
            self.assertIn("sha256=", manifest)

    def test_compact_local_understanding_preserves_model_report_and_gate(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            attempt = repo / "runtime" / "attempt-01"
            task = Task(
                "task-fe-01",
                ".opencode/commands/task-fe-01.md",
                "Bootstrap Angular",
                ("frontend/**",),
                ("true",),
                "commit",
            )
            stdout = json.dumps(
                {
                    "type": "message",
                    "part": {
                        "type": "text",
                        "text": (
                            "# Local understanding report\n\n"
                            "## Task objective in my own words\nBootstrap Angular."
                        ),
                    },
                }
            )
            gate = CommandResult(("true",), 0, "green", "", False)

            runner._write_compact_local_understanding(
                attempt, stdout, task, gate
            )

            report = (attempt / "evidence" / "local-understanding.md").read_text(
                encoding="utf-8"
            )
            self.assertIn("Bootstrap Angular", report)
            self.assertIn("Controller-verified post-edit evidence", report)
            self.assertIn("exit code: `0`", report)

    def test_file_change_notification_uses_success_sound_mode(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            notify = repo / "scripts" / "notify-success.sh"
            notify.parent.mkdir(parents=True)
            notify.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            notify.chmod(0o755)
            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            runner.notify_script = notify
            captured = []

            def fake_run_logged(name, command, target, **kwargs):
                captured.append((name, tuple(command), target, kwargs))
                return CommandResult(tuple(command), 0, "", "", False)

            runner._run_logged = fake_run_logged
            event_dir = repo / "runtime" / "attempt-01"

            runner._notify_file_changed(
                event_dir,
                "files-changed-01",
                "task-fe-01: local LLM changed repository files",
            )

            self.assertEqual(1, len(captured))
            self.assertEqual("--file-changed", captured[0][1][1])
            self.assertIn("local LLM changed", captured[0][1][2])

    def test_failed_assimilation_becomes_reviewable_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            attempt = repo / "runtime" / "attempt-01"
            result = CommandResult(
                ("opencode",),
                124,
                "",
                "local model timed out",
                timed_out=True,
            )

            runner._write_failed_local_understanding(attempt, result)

            report = (attempt / "evidence" / "local-understanding.md").read_text(
                encoding="utf-8"
            )
            self.assertIn("failed with exit 124", report)
            self.assertIn("local model timed out", report)
            self.assertIn("Codex must review", report)

    def test_no_progress_recovery_demands_exact_checklist_mapping(self):
        runner = object.__new__(AutomaticRunner)

        action = runner._no_progress_action("Fix exact headings.")

        self.assertIn("checklist", action)
        self.assertIn("exact code or test assertion", action)
        self.assertIn("Fix exact headings", action)

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

    def test_progress_active_task_is_authoritative_for_task_selection(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = self._selection_runner(Path(directory))
            runner.progress["active_task"] = "task-02"
            runner.lock_path.parent.mkdir(parents=True)
            runner.lock_path.write_text(
                json.dumps({"schema_version": 1, "task_id": "task-01"}),
                encoding="utf-8",
            )

            selected = runner._select_task()

            self.assertIsNotNone(selected)
            self.assertEqual("task-02", selected.id)

    def test_without_active_progress_selects_first_non_accepted_task(self):
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

    def test_unlocked_resume_accepts_maintenance_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = self._selection_runner(Path(directory))
            task = runner._select_task()

            runner._validate_unlocked_resume((
                "scripts/run-gallery-agent.sh",
                "scripts/select-r4r-destination.sh",
                "opencode.jsonc",
            ), task)

    def test_unlocked_resume_accepts_in_scope_product_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = self._selection_runner(Path(directory))
            task = runner._select_task()

            runner._validate_unlocked_resume((
                "src/main/App.java",
                "src/test/AppTest.java",
            ), task)

    def test_unlocked_resume_rejects_out_of_scope_dirty_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = self._selection_runner(Path(directory))
            task = runner._select_task()

            with self.assertRaisesRegex(RuntimeError, "out-of-scope paths"):
                runner._validate_unlocked_resume(("pom.xml",), task)


    def test_unlocked_resume_accepts_downloaded_r4r_artifacts(self):
        with tempfile.TemporaryDirectory() as directory:
            runner = self._selection_runner(Path(directory))
            task = runner._select_task()

            runner._validate_unlocked_resume((
                "r4r-laptop.zip",
                "r4r-spring-ai.zip",
                "fix-r4r-opencode-baseurls-v1.sh",
                "payload/py-codex-agent/runner.py",
                "SHA256SUMS.txt",
            ), task)

    def test_scoped_commit_excludes_downloaded_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self._init_repo(repo)
            product = repo / "src" / "main" / "App.java"
            product.parent.mkdir(parents=True)
            product.write_text("class App {}\n", encoding="utf-8")
            archive = repo / "r4r-laptop.zip"
            archive.write_bytes(b"not product")
            run_command(("git", "add", "r4r-laptop.zip"), repo)

            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            runner.timeout = 30
            commit = runner._commit_if_needed("task commit", ("src/**",))

            self.assertIsNotNone(commit)
            committed = run_command(
                ("git", "show", "--name-only", "--pretty=", "HEAD"), repo
            ).stdout.splitlines()
            self.assertEqual(["src/main/App.java"], committed)
            status = run_command(("git", "status", "--short"), repo).stdout
            self.assertIn("r4r-laptop.zip", status)

    def test_opencode_runtime_config_rejects_unresolved_url(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            agents = repo / ".opencode" / "agents"
            agents.mkdir(parents=True)
            (agents / "r4r-pc.md").write_text(
                "model: ollama-pc/model\n", encoding="utf-8"
            )
            config = repo / "opencode.jsonc"
            config.write_text(json.dumps({
                "provider": {
                    "ollama-pc": {
                        "options": {"baseURL": "{env:MISSING}"},
                        "models": {"model": {}},
                    }
                }
            }), encoding="utf-8")
            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            runner.opencode_agent = "r4r-pc"

            with patch.dict(os.environ, {"OPENCODE_CONFIG": str(config)}):
                with self.assertRaisesRegex(RuntimeError, "unresolved baseURL"):
                    runner._validate_opencode_runtime_config()

    def test_opencode_runtime_config_accepts_absolute_url(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            agents = repo / ".opencode" / "agents"
            agents.mkdir(parents=True)
            (agents / "r4r-pc.md").write_text(
                "model: ollama-pc/model\n", encoding="utf-8"
            )
            config = repo / "opencode.jsonc"
            config.write_text(json.dumps({
                "provider": {
                    "ollama-pc": {
                        "options": {"baseURL": "http://127.0.0.1:11434/v1"},
                        "models": {"model": {}},
                    }
                }
            }), encoding="utf-8")
            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            runner.opencode_agent = "r4r-pc"

            with patch.dict(os.environ, {"OPENCODE_CONFIG": str(config)}):
                runner._validate_opencode_runtime_config()

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
