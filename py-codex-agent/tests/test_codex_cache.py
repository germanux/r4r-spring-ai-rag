from pathlib import Path
import tempfile
import unittest

from r4r_codex_agent.runner import AutomaticRunner


class CodexPlanCacheTest(unittest.TestCase):
    def test_reuses_only_identical_diagnostics_within_cooldown(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            runner = object.__new__(AutomaticRunner)
            runner.repo = repo
            runner.control_dir = repo / "runtime/control"
            runner.control_dir.mkdir(parents=True)
            runner.codex_plan_cache_path = runner.control_dir / "codex-plan-cache.json"
            runner.codex_min_interval_seconds = 3600
            plan = {
                "schema_version": 1,
                "decision": "READY",
                "task_id": "task-03",
                "summary": "repair compilation",
                "instructions": ["Fix the named source."],
                "focus_paths": ["src/test/java/example/BrokenIT.java"],
                "verification": ["Run the exact gate."],
            }

            runner._store_cached_codex_plan("task-03", "same", plan)

            self.assertEqual(
                plan,
                runner._load_cached_codex_plan("task-03", "same"),
            )
            self.assertIsNone(runner._load_cached_codex_plan("task-03", "changed"))
            self.assertIsNone(runner._load_cached_codex_plan("task-04", "same"))


if __name__ == "__main__":
    unittest.main()
