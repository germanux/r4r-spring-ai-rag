import json
from pathlib import Path
import tempfile
import unittest

from r4r_orchestrator.contracts import load_task, validate_decision


class ContractsTest(unittest.TestCase):
    def test_loads_strict_task(self):
        payload = {
            "schema_version": 1,
            "id": "task-1",
            "benchmark": "benchmarks/01-base.md",
            "objective": "Keep green",
            "allowed_paths": ["src/**"],
            "pre_gate": ["true"],
            "post_gate": ["true"],
            "opencode_prompt": ".opencode/commands/resume.md",
            "review_required": True,
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "task.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            task = load_task(path)
        self.assertEqual("task-1", task.id)

    def test_rejects_review_for_another_task(self):
        decision = {
            "schema_version": 1,
            "decision": "ACCEPT",
            "task_id": "other",
            "summary": "Green",
            "paths": [],
            "next_action": "Commit manually",
        }
        with self.assertRaises(ValueError):
            validate_decision(decision, "task-1")


if __name__ == "__main__":
    unittest.main()
