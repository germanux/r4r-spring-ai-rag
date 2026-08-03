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
            run_dir = root / "evidence"
            command = ring_loop._command(paths, run_dir, "run-1")
            self.assertIn("--agent", command)
            self.assertIn("r4r-ring", command)
            self.assertIn("--model", command)
            self.assertIn("t033-128k", " ".join(command))
            self.assertEqual(command[command.index("--dir") + 1], str(paths.ring))
            prompt = command[-1]
            self.assertIn(str(run_dir), prompt)
            self.assertIn(str(run_dir / "output"), prompt)
            self.assertIn("RUN_ID: run-1", prompt)
            self.assertIn("Write these six staged files", prompt)
            self.assertIn("Do not write `runtime/control/**`", prompt)
            self.assertIn(str(paths.ring), prompt)
            self.assertNotIn(str(paths.pc), prompt)
            self.assertNotIn(str(paths.lp), prompt)
            self.assertIn("never delete, unlink, remove, rename or move", prompt)
            self.assertIn("may additionally make bounded, non-destructive edits", prompt)
            self.assertIn("`docs/agent-coordination/`", prompt)

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


    def test_staged_outputs_are_validated_promoted_and_directives_derived(self) -> None:
        from datetime import datetime
        import json

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            ring = root / "ring"
            paths = WorktreePaths(ring, root / "pc", root / "lp")
            run_id = "20260803T160000Z"
            run_dir = ring / "runtime" / "ring-agent" / "ring" / run_id
            output = run_dir / "output"
            output.mkdir(parents=True)

            evidence = {}
            for worker in ("PC", "LP"):
                path = run_dir / f"{worker.lower()}-runtime" / "progress.json"
                path.parent.mkdir(parents=True)
                path.write_text("{}\n", encoding="utf-8")
                evidence[worker] = path

            state = {
                "schema_version": 1,
                "run_id": run_id,
                "overall_status": "READY",
                "decisions": {
                    worker: {
                        "action": "CONTINUE",
                        "task_id": f"task-{worker.lower()}",
                        "reason": f"{worker} evidence requires one correction",
                        "next_action": f"apply the focused {worker} correction",
                        "evidence_paths": [str(evidence[worker])],
                        "acceptance_gates": [f"run the exact {worker} gate"],
                        "avoid_repeating": "do not repeat the rejected approach",
                    }
                    for worker in ("PC", "LP")
                },
                "integration_risks": [],
                "evidence_limitations": [],
            }
            (output / "state.json").write_text(
                json.dumps(state), encoding="utf-8"
            )
            for name in ring_loop.STAGED_OUTPUT_NAMES:
                if name != "state.json":
                    (output / name).write_text(f"# {name}\nverified\n", encoding="utf-8")

            publication = ring_loop._publish_staged_outputs(
                paths, run_dir, run_id
            )

            self.assertTrue(publication["published"], publication)
            self.assertTrue((ring / ".ring-agent" / "state.json").is_file())
            self.assertTrue(
                (ring / ".opencode/current/ring/worker-understanding.md").is_file()
            )
            coordination = ring / "docs" / "agent-coordination"
            self.assertTrue((coordination / "CURRENT-STATE.md").is_file())
            self.assertTrue((coordination / "PC-WORKER.md").is_file())
            self.assertTrue((coordination / "LAPTOP-WORKER.md").is_file())
            self.assertTrue((coordination / "RING-HANDOFF.md").is_file())
            self.assertTrue((coordination / "WORKER-UNDERSTANDING.md").is_file())
            decisions = (coordination / "DECISIONS.md").read_text(encoding="utf-8")
            self.assertIn(f"## Cycle `{run_id}`", decisions)
            self.assertIn("Decision: `CONTINUE`", decisions)
            self.assertEqual(
                publication["coordination_commit"]["status"],
                "not-git-worktree",
            )
            ring_loop._publish_staged_outputs(paths, run_dir, run_id)
            decisions = (coordination / "DECISIONS.md").read_text(encoding="utf-8")
            self.assertEqual(decisions.count(f"## Cycle `{run_id}`"), 1)
            for worker in ("PC", "LP"):
                directive_path = (
                    ring / "runtime" / "control" / worker / "ring-qwen3-directive.json"
                )
                directive = json.loads(directive_path.read_text(encoding="utf-8"))
                self.assertEqual(directive["target"], worker)
                self.assertEqual(directive["priority"], "advisory")
                self.assertEqual(directive["task_id"], f"task-{worker.lower()}")
                self.assertTrue(directive["next_action"])
                self.assertTrue(directive["evidence_paths"])
                self.assertIsNotNone(datetime.fromisoformat(directive["expires_at"]))

    def test_coordination_commit_preserves_unrelated_staged_changes(self) -> None:
        import subprocess

        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
            subprocess.run(
                ["git", "config", "user.name", "R4R test"],
                cwd=repo,
                check=True,
            )
            subprocess.run(
                ["git", "config", "user.email", "r4r@example.invalid"],
                cwd=repo,
                check=True,
            )
            unrelated = repo / "unrelated.txt"
            unrelated.write_text("preserve me\n", encoding="utf-8")
            subprocess.run(["git", "add", "unrelated.txt"], cwd=repo, check=True)
            coordination = repo / "docs" / "agent-coordination" / "CURRENT-STATE.md"
            coordination.parent.mkdir(parents=True)
            coordination.write_text("# current state\n", encoding="utf-8")

            result = ring_loop._commit_coordination_files(
                repo,
                [coordination],
                "run-1",
            )

            self.assertEqual(result["status"], "committed", result)
            committed = subprocess.run(
                ["git", "show", "--pretty=format:", "--name-only", "HEAD"],
                cwd=repo,
                text=True,
                stdout=subprocess.PIPE,
                check=True,
            ).stdout
            self.assertIn("docs/agent-coordination/CURRENT-STATE.md", committed)
            self.assertNotIn("unrelated.txt", committed)
            staged = subprocess.run(
                ["git", "diff", "--cached", "--name-only"],
                cwd=repo,
                text=True,
                stdout=subprocess.PIPE,
                check=True,
            ).stdout
            self.assertIn("unrelated.txt", staged)



if __name__ == "__main__":
    unittest.main()
