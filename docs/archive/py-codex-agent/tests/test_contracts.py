import json
from pathlib import Path
import tempfile
import unittest

from r4r_codex_agent.contracts import (
    default_progress,
    load_progress,
    load_task_plan,
    validate_structured_result,
)


class ContractsTest(unittest.TestCase):
    def test_loads_ordered_task_plan(self):
        payload = {
            "schema_version": 1,
            "tasks": [{
                "id": "task-01-base",
                "command": ".opencode/commands/task-01-base.md",
                "objective": "Keep green",
                "allowed_paths": ["src/**"],
                "gate": ["./scripts/task-gate.sh", "task-01-base"],
                "commit_message": "chore: baseline",
            }],
            "final_gate": ["./scripts/task-gate.sh", "all"],
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "plan.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            plan = load_task_plan(path)
        self.assertEqual("task-01-base", plan.tasks[0].id)

    def test_progress_adds_new_subtasks_without_losing_history(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "progress.json"
            progress = default_progress(["task-01", "task-06", "task-07"])
            progress["tasks"][0]["status"] = "ACCEPTED"
            progress["tasks"][0]["accepted_at"] = "2026-08-01T00:00:00+00:00"
            progress["tasks"][1]["status"] = "ACTIVE"
            progress["active_task"] = "task-06"
            path.write_text(json.dumps(progress), encoding="utf-8")

            loaded = load_progress(
                path,
                ["task-01", "task-06", "task-06b", "task-06c", "task-07"],
            )

        self.assertEqual(
            ["task-01", "task-06", "task-06b", "task-06c", "task-07"],
            [item["id"] for item in loaded["tasks"]],
        )
        self.assertEqual("ACCEPTED", loaded["tasks"][0]["status"])
        self.assertEqual("ACTIVE", loaded["tasks"][1]["status"])
        self.assertEqual("PENDING", loaded["tasks"][2]["status"])
        self.assertEqual("PENDING", loaded["tasks"][3]["status"])
        self.assertEqual("task-06", loaded["active_task"])

    def test_progress_rejects_removed_or_unknown_tasks(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "progress.json"
            path.write_text(json.dumps(default_progress(["unknown"])), encoding="utf-8")
            with self.assertRaises(ValueError):
                load_progress(path, ["task-01-base"])

    def test_rejects_result_for_another_task(self):
        with self.assertRaises(ValueError):
            validate_structured_result(
                {"schema_version": 1, "decision": "ACCEPT", "task_id": "other"},
                "task-01-base",
                {"ACCEPT"},
            )


if __name__ == "__main__":
    unittest.main()
