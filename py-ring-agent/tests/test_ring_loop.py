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

    def test_worker_snapshot_includes_memory_and_latest_checkpoint(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            worker = root / "pc"
            ring = root / "ring"
            memory = worker / ".opencode" / "memory.backend.md"
            progress = worker / ".opencode" / "progress.backend.json"
            memory.parent.mkdir(parents=True)
            memory.write_text("# memory\nStill unproven\n", encoding="utf-8")
            progress.write_text('{"schema_version":1}\n', encoding="utf-8")
            checkpoint = (
                worker / "runtime" / "runs" / "PC" / "run-1" / "task" /
                "attempt-01" / "evidence" / "checkpoint.json"
            )
            checkpoint.parent.mkdir(parents=True)
            checkpoint.write_text('{"status":"created"}\n', encoding="utf-8")
            run_dir = ring / "runtime" / "ring-agent" / "ring" / "session"

            ring_loop._write_worker_runtime_evidence(
                "PC", worker, ring, run_dir
            )

            snapshot = run_dir / "pc-runtime"
            self.assertTrue((snapshot / "memory.md").is_file())
            self.assertTrue((snapshot / "checkpoint.json").is_file())
            manifest = (snapshot / "manifest.json").read_text(encoding="utf-8")
            self.assertIn("memory.backend.md", manifest)
            self.assertIn("checkpoint.json", manifest)

    def test_consumes_worker_event_requests_once(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            ring = Path(temporary) / "ring"
            request = ring / "runtime" / "control" / "RING" / "requests" / "LP.json"
            request.parent.mkdir(parents=True)
            request.write_text(
                '{"schema_version":1,"worker":"LP","reason":"codex-revise"}\n',
                encoding="utf-8",
            )
            run_dir = ring / "runtime" / "ring-agent" / "ring" / "session"
            run_dir.mkdir(parents=True)

            consumed = ring_loop._consume_worker_requests(ring, run_dir)

            self.assertEqual("LP", consumed[0]["worker"])
            self.assertFalse(request.exists())
            self.assertTrue((run_dir / "worker-requests" / "LP.json").is_file())
            self.assertTrue((run_dir / "worker-request-manifest.json").is_file())



if __name__ == "__main__":
    unittest.main()
