import json
from pathlib import Path
import tempfile
import unittest

from r4r_codex_agent.contracts import default_progress, load_progress, load_task_plan, validate_structured_result


class ContractsTest(unittest.TestCase):
    def test_loads_ordered_task_plan(self):
        payload = {
            "schema_version": 1,
            "tasks": [{
                "id": "task-01-base", "command": ".opencode/commands/task-01-base.md",
                "objective": "Keep green", "allowed_paths": ["src/**"],
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

    def test_progress_must_match_plan_order(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "progress.json"
            path.write_text(json.dumps(default_progress(["wrong"])), encoding="utf-8")
            with self.assertRaises(ValueError):
                load_progress(path, ["task-01-base"])

    def test_rejects_result_for_another_task(self):
        with self.assertRaises(ValueError):
            validate_structured_result(
                {"schema_version": 1, "decision": "ACCEPT", "task_id": "other"},
                "task-01-base", {"ACCEPT"},
            )


if __name__ == "__main__":
    unittest.main()
