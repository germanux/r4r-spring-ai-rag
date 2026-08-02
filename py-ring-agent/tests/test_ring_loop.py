from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

from r4r_ring_agent import ring_loop
from r4r_ring_agent.worktrees import WorktreePaths


class RingLoopTest(unittest.TestCase):
    def test_command_uses_frontier_override_and_ring_worktree(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = WorktreePaths(root / "ring", root / "pc", root / "lp")
            command = ring_loop._command(paths, root / "evidence")
            self.assertIn("--agent", command)
            self.assertIn("r4r-ring", command)
            self.assertIn("--model", command)
            self.assertIn("t033-128k", " ".join(command))
            self.assertEqual(command[command.index("--dir") + 1], str(paths.ring))
            prompt = command[-1]
            self.assertIn(str(paths.pc), prompt)
            self.assertIn(str(paths.lp), prompt)
            self.assertIn("ring-qwen3-directive.json", prompt)
            self.assertIn("priority", prompt)
            self.assertIn("advisory", prompt)

    def test_directive_validation_accepts_current_advisory_json(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / "ring-qwen3-directive.json"
            from datetime import datetime, timezone
            import json
            path.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "target": "PC",
                        "task_id": "task-06",
                        "generated_at": datetime.now(timezone.utc).isoformat(),
                        "priority": "advisory",
                        "next_action": "Run the real CLI smoke.",
                    }
                ),
                encoding="utf-8",
            )
            result = ring_loop._validate_directive(path, "PC")
            self.assertTrue(result["valid"])
            self.assertEqual(result["reason"], "ok")


if __name__ == "__main__":
    unittest.main()
