from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import sys
import tempfile
import types
import unittest

from r4r_worker.contracts import Task, load_progress
from r4r_worker.runner import AutomaticRunner
from r4r_ring_agent.assignment import (
    load_global_progress,
    record_global_acceptance,
    validate_assignment,
)


def _load_ring_loop():
    operator = types.ModuleType("r4r_ring_agent.operator_control")
    operator.OperatorCommand = object
    operator.RingCommandFile = object
    process = types.ModuleType("r4r_ring_agent.ring_process")
    process.run_streamed = lambda *args, **kwargs: None
    worktrees = types.ModuleType("r4r_ring_agent.worktrees")

    @dataclass(frozen=True)
    class WorktreePaths:
        ring: Path
        pc: Path
        lp: Path

    worktrees.WorktreePaths = WorktreePaths
    worktrees.require_git_worktree = lambda path, _label: path
    sys.modules[operator.__name__] = operator
    sys.modules[process.__name__] = process
    sys.modules[worktrees.__name__] = worktrees
    from r4r_ring_agent import ring_loop

    return ring_loop


class ProgressMigrationTests(unittest.TestCase):
    def test_in_progress_is_valid_and_legacy_progress_expands(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            legacy = root / "progress.backend.json"
            legacy.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "active_task": "task-01",
                        "last_run": None,
                        "tasks": [
                            {
                                "id": "task-01",
                                "status": "IN_PROGRESS",
                                "accepted_at": None,
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            previous = os.environ.get("R4R_LEGACY_PROGRESS_PATH")
            os.environ["R4R_LEGACY_PROGRESS_PATH"] = str(legacy)
            try:
                value = load_progress(
                    root / "progress.pc.json",
                    ("task-01", "task-02"),
                )
            finally:
                if previous is None:
                    os.environ.pop("R4R_LEGACY_PROGRESS_PATH", None)
                else:
                    os.environ["R4R_LEGACY_PROGRESS_PATH"] = previous

            self.assertEqual(value["tasks"][0]["status"], "IN_PROGRESS")
            self.assertEqual(value["tasks"][1]["status"], "PENDING")


class AssignmentTests(unittest.TestCase):
    def test_worker_accepts_only_fresh_exact_scope_assignment(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "assignment.json"
            task = Task(
                id="task-01",
                command=".opencode/commands/task-01.md",
                objective="objective",
                allowed_paths=("src/**",),
                gate=("true",),
                commit_message="test: task",
            )
            now = datetime.now(timezone.utc)
            assignment = {
                "schema_version": 1,
                "assignment_id": "run-1:PC:task-01",
                "target": "PC",
                "priority": "advisory",
                "action": "START",
                "task_id": task.id,
                "write_scope": list(task.allowed_paths),
                "generated_at": now.isoformat(),
                "expires_at": (now + timedelta(minutes=10)).isoformat(),
            }
            path.write_text(json.dumps(assignment), encoding="utf-8")
            runner = object.__new__(AutomaticRunner)
            runner.ring_directive_path = path
            runner.worker_id = "PC"
            runner.ring_directive_max_age_seconds = 10800
            runner.global_progress_path = None
            runner.plan = types.SimpleNamespace(tasks=(task,))
            runner._task_by_id = lambda task_id: task if task_id == task.id else None

            self.assertEqual(runner._active_assignment()["task_id"], task.id)

            assignment["write_scope"] = ["frontend/**"]
            path.write_text(json.dumps(assignment), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "scope does not match"):
                runner._active_assignment()

            assignment["write_scope"] = list(task.allowed_paths)
            assignment["expires_at"] = (now - timedelta(seconds=1)).isoformat()
            path.write_text(json.dumps(assignment), encoding="utf-8")
            self.assertIsNone(runner._active_assignment())

    def test_dependencies_and_global_acceptance_are_deterministic(self) -> None:
        task = Task(
            id="task-02",
            command="task.md",
            objective="objective",
            allowed_paths=("src/**",),
            gate=("true",),
            commit_message="test: task",
            depends_on=("task-01",),
        )
        now = datetime.now(timezone.utc)
        assignment = {
            "schema_version": 1,
            "assignment_id": "run-1:PC:task-02",
            "target": "PC",
            "priority": "advisory",
            "action": "START",
            "task_id": "task-02",
            "write_scope": ["src/**"],
            "generated_at": now.isoformat(),
            "expires_at": (now + timedelta(minutes=10)).isoformat(),
        }
        with self.assertRaisesRegex(ValueError, "unmet dependencies"):
            validate_assignment(
                assignment,
                worker="PC",
                tasks={task.id: task},
                accepted_task_ids=(),
                max_age_seconds=10800,
            )
        validated = validate_assignment(
            assignment,
            worker="PC",
            tasks={task.id: task},
            accepted_task_ids=("task-01",),
            max_age_seconds=10800,
        )
        self.assertEqual(validated["task_id"], "task-02")

    def test_ring_can_validate_an_inactive_hold_directive(self) -> None:
        now = datetime.now(timezone.utc)
        directive = {
            "schema_version": 1,
            "assignment_id": "run-1:PC:NO_ACTIVE_TASK",
            "target": "PC",
            "priority": "advisory",
            "action": "HOLD",
            "task_id": "NO_ACTIVE_TASK",
            "write_scope": [],
            "generated_at": now.isoformat(),
            "expires_at": (now + timedelta(minutes=10)).isoformat(),
        }

        validated = validate_assignment(
            directive,
            worker="PC",
            tasks={},
            max_age_seconds=10800,
            require_active=False,
        )

        self.assertEqual("HOLD", validated["action"])

    def test_global_acceptance_is_idempotent_and_conflict_safe(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            ledger = Path(directory) / "global-progress.json"
            record_global_acceptance(
                ledger,
                task_id="task-01",
                worker="PC",
                commit="a" * 40,
            )
            record_global_acceptance(
                ledger,
                task_id="task-01",
                worker="PC",
                commit="a" * 40,
            )
            self.assertIn("task-01", load_global_progress(ledger)["accepted"])
            with self.assertRaisesRegex(RuntimeError, "already globally accepted"):
                record_global_acceptance(
                    ledger,
                    task_id="task-01",
                    worker="LP",
                    commit="b" * 40,
                )


class RingEscalationTests(unittest.TestCase):
    def test_luna_escalate_is_detected_but_not_a_final_action(self) -> None:
        ring_loop = _load_ring_loop()
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            (output / "state.json").write_text(
                json.dumps(
                    {
                        "decisions": {
                            "PC": {"action": "ESCALATE"},
                            "LP": {"action": "NO_ACTION"},
                        }
                    }
                ),
                encoding="utf-8",
            )
            self.assertEqual(ring_loop._draft_escalations(output), ("PC",))
            self.assertNotIn("ESCALATE", ring_loop.VALID_RING_ACTIONS)


class CanonicalConfigurationTests(unittest.TestCase):
    def test_models_and_single_plan_match_selected_architecture(self) -> None:
        root = Path(__file__).resolve().parents[2]
        config = json.loads((root / "config/r4r-agents.json").read_text())
        self.assertEqual(config["agents"]["RING"]["model"], "gpt-5.6-luna")
        self.assertEqual(config["agents"]["PC"]["model"], "gpt-5.3-codex")
        self.assertEqual(config["agents"]["LP"]["model"], "gpt-5.3-codex")
        self.assertEqual(config["agents"]["ESCALATION"]["model"], "gpt-5.3-codex")
        self.assertEqual(config["agents"]["RING"]["reasoningVariant"], "low")
        self.assertEqual(config["agents"]["PC"]["reasoningVariant"], "low")
        self.assertEqual(config["agents"]["LP"]["reasoningVariant"], "low")
        self.assertEqual(config["agents"]["ESCALATION"]["reasoningVariant"], "high")
        self.assertEqual(
            config["agents"]["PC"]["plan"],
            config["agents"]["LP"]["plan"],
        )
        plan = json.loads((root / ".opencode/task-plan.json").read_text())
        task_ids = [item["id"] for item in plan["tasks"]]
        self.assertEqual(len(task_ids), len(set(task_ids)))
        self.assertTrue(plan["assignment_policy"]["workers_are_fullstack"])


if __name__ == "__main__":
    unittest.main()
