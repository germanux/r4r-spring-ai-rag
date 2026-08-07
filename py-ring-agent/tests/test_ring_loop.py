from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from r4r_ring_agent import ring_loop
from r4r_ring_agent.worktrees import WorktreePaths


def _configure_task_plans(
    ring: Path,
    *,
    pc_scope: list[str] | None = None,
    lp_scope: list[str] | None = None,
) -> None:
    plans = {
        "PC": (".opencode/task-plan.backend.json", "task-pc", pc_scope or ["src/**"]),
        "LP": (
            ".opencode/task-plan.frontend.json",
            "task-lp",
            lp_scope or ["frontend/**"],
        ),
    }
    config = {"agents": {}}
    for worker, (plan_name, task_id, scope) in plans.items():
        config["agents"][worker] = {
            "agentId": "r4r-pc" if worker == "PC" else "r4r-lp",
            "branch": (
                "agent/pc-qwen3-worker"
                if worker == "PC"
                else "agent/laptop-qwen3-worker"
            ),
            "model": "gpt-5.6-terra",
            "plan": plan_name,
        }
        plan_path = ring / plan_name
        plan_path.parent.mkdir(parents=True, exist_ok=True)
        plan_path.write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "tasks": [{"id": task_id, "allowed_paths": scope}],
                }
            ),
            encoding="utf-8",
        )
    config_path = ring / "config" / "r4r-agents.json"
    config_path.parent.mkdir(parents=True, exist_ok=True)
    config_path.write_text(json.dumps(config), encoding="utf-8")
    subprocess.run(["git", "init", "-q"], cwd=ring, check=True)
    subprocess.run(
        ["git", "config", "user.name", "R4R Test"],
        cwd=ring,
        check=True,
    )
    subprocess.run(
        ["git", "config", "user.email", "r4r-test@example.invalid"],
        cwd=ring,
        check=True,
    )


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
            self.assertEqual(
                command[command.index("--model") + 1],
                "openai/gpt-5.6-luna",
            )
            self.assertIn("--variant", command)
            self.assertEqual(command[command.index("--variant") + 1], "low")
            self.assertEqual(command[command.index("--dir") + 1], str(paths.ring))
            prompt = command[-1]
            self.assertIn(str(run_dir), prompt)
            self.assertIn(str(run_dir / "output"), prompt)
            self.assertIn("RUN_ID: run-1", prompt)
            self.assertIn("Write these six staged files", prompt)
            self.assertIn("`runtime/control/**`", prompt)
            self.assertIn(str(paths.ring), prompt)
            self.assertNotIn(str(paths.pc), prompt)
            self.assertNotIn(str(paths.lp), prompt)
            self.assertIn("global-progress.json", prompt)
            self.assertIn("ESCALATE", prompt)
            self.assertIn("Never assign overlapping", prompt)

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
                        "assignment_id": "run-1:PC:task-06",
                        "target": "PC",
                        "task_id": "task-06",
                        "action": "CONTINUE",
                        "generated_at": datetime.now(timezone.utc).isoformat(),
                        "expires_at": (
                            datetime.now(timezone.utc)
                            + __import__("datetime").timedelta(minutes=10)
                        ).isoformat(),
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
            _configure_task_plans(ring)
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
                (ring / ".ring-agent" / "worker-understanding.md").is_file()
            )
            self.assertEqual(
                2,
                len(list((ring / ".ring-agent" / "evidence").glob("*/*.md"))),
            )
            self.assertTrue(
                (
                    ring
                    / ".ring-agent/evidence/task-pc/r4r-pc-attempt-01.md"
                ).is_file()
            )
            self.assertTrue(
                (
                    ring
                    / ".ring-agent/evidence/task-lp/r4r-lp-attempt-01.md"
                ).is_file()
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
            self.assertIn("Decision fingerprint:", decisions)
            self.assertEqual(
                publication["coordination_commit"]["status"],
                "committed",
            )
            ring_loop._publish_staged_outputs(paths, run_dir, run_id)
            decisions = (coordination / "DECISIONS.md").read_text(encoding="utf-8")
            self.assertEqual(decisions.count(f"## Cycle `{run_id}`"), 1)

            second_run_id = "20260803T170000Z"
            second_run_dir = ring / "runtime" / "ring-agent" / "ring" / second_run_id
            second_output = second_run_dir / "output"
            second_output.mkdir(parents=True)
            second_state = json.loads(json.dumps(state))
            second_state["run_id"] = second_run_id
            for worker in ("PC", "LP"):
                path = second_run_dir / f"{worker.lower()}-runtime" / "progress.json"
                path.parent.mkdir(parents=True)
                path.write_text("{}\n", encoding="utf-8")
                second_state["decisions"][worker]["evidence_paths"] = [str(path)]
            (second_output / "state.json").write_text(
                json.dumps(second_state), encoding="utf-8"
            )
            for name in ring_loop.STAGED_OUTPUT_NAMES:
                if name != "state.json":
                    (second_output / name).write_text(
                        f"# regenerated {name}\nno semantic change\n",
                        encoding="utf-8",
                    )

            second_publication = ring_loop._publish_staged_outputs(
                paths, second_run_dir, second_run_id
            )

            self.assertTrue(second_publication["published"], second_publication)
            self.assertFalse(second_publication["semantic_change"])
            self.assertEqual(
                "no-semantic-change",
                second_publication["coordination_commit"]["status"],
            )
            self.assertEqual(
                2,
                len(list((ring / ".ring-agent" / "evidence").glob("*/*.md"))),
            )
            decisions = (coordination / "DECISIONS.md").read_text(encoding="utf-8")
            self.assertNotIn(f"## Cycle `{second_run_id}`", decisions)
            self.assertNotIn(
                "no semantic change",
                (coordination / "CURRENT-STATE.md").read_text(encoding="utf-8"),
            )
            for worker in ("PC", "LP"):
                directive_path = (
                    ring / "runtime" / "control" / worker / "assignment.json"
                )
                directive = json.loads(directive_path.read_text(encoding="utf-8"))
                self.assertEqual(directive["target"], worker)
                self.assertEqual(directive["priority"], "advisory")
                self.assertEqual(directive["task_id"], f"task-{worker.lower()}")
                self.assertTrue(directive["next_action"])
                self.assertTrue(directive["evidence_paths"])
                self.assertTrue(directive["write_scope"])
                self.assertIn(directive["assigned_agent"], {"r4r-pc", "r4r-lp"})
                self.assertTrue(directive["branch"].startswith("agent/"))
                self.assertEqual(directive["model"], "gpt-5.6-terra")
                self.assertTrue(directive["assignment_id"])
                self.assertTrue(directive["evidence_path"].startswith(
                    f".ring-agent/evidence/task-{worker.lower()}/"
                ))
                self.assertIsNotNone(datetime.fromisoformat(directive["expires_at"]))

    def test_overlapping_active_task_scopes_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            ring = Path(temporary) / "ring"
            _configure_task_plans(
                ring,
                pc_scope=["src/**"],
                lp_scope=["src/main/**"],
            )
            state = {
                "decisions": {
                    "PC": {
                        "action": "CONTINUE",
                        "task_id": "task-pc",
                    },
                    "LP": {
                        "action": "START",
                        "task_id": "task-lp",
                    },
                }
            }

            with self.assertRaisesRegex(ValueError, "overlapping write_scope"):
                ring_loop._load_task_assignments(ring, state)

    def test_identical_semantic_cycle_does_not_create_second_git_commit(self) -> None:
        import subprocess

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            ring = root / "ring"
            ring.mkdir()
            subprocess.run(["git", "init", "-q"], cwd=ring, check=True)
            subprocess.run(
                ["git", "config", "user.name", "R4R test"], cwd=ring, check=True
            )
            subprocess.run(
                ["git", "config", "user.email", "r4r@example.invalid"],
                cwd=ring,
                check=True,
            )
            baseline = ring / "baseline.txt"
            baseline.write_text("baseline\n", encoding="utf-8")
            subprocess.run(["git", "add", "baseline.txt"], cwd=ring, check=True)
            subprocess.run(
                ["git", "commit", "-q", "-m", "baseline"], cwd=ring, check=True
            )
            _configure_task_plans(ring)
            paths = WorktreePaths(ring, root / "pc", root / "lp")

            def publish(run_id: str, summary: str) -> dict[str, object]:
                run_dir = ring / "runtime" / "ring-agent" / "ring" / run_id
                output = run_dir / "output"
                output.mkdir(parents=True)
                decisions = {}
                for worker in ("PC", "LP"):
                    evidence = run_dir / f"{worker.lower()}-runtime" / "progress.json"
                    evidence.parent.mkdir(parents=True)
                    evidence.write_text("{}\n", encoding="utf-8")
                    decisions[worker] = {
                        "action": "CONTINUE",
                        "task_id": f"task-{worker.lower()}",
                        "reason": f"{worker} still needs the same correction",
                        "next_action": f"apply the focused {worker} correction",
                        "evidence_paths": [str(evidence)],
                        "acceptance_gates": [f"run the exact {worker} gate"],
                        "avoid_repeating": "do not repeat the rejected approach",
                    }
                state = {
                    "schema_version": 1,
                    "run_id": run_id,
                    "overall_status": "READY",
                    "decisions": decisions,
                    "integration_risks": [],
                    "evidence_limitations": [],
                }
                (output / "state.json").write_text(
                    json.dumps(state), encoding="utf-8"
                )
                for name in ring_loop.STAGED_OUTPUT_NAMES:
                    if name != "state.json":
                        (output / name).write_text(
                            f"# {name}\n{summary}\n", encoding="utf-8"
                        )
                return ring_loop._publish_staged_outputs(paths, run_dir, run_id)

            first = publish("20260803T160000Z", "first wording")
            first_head = subprocess.run(
                ["git", "rev-parse", "HEAD"],
                cwd=ring,
                text=True,
                stdout=subprocess.PIPE,
                check=True,
            ).stdout.strip()
            second = publish("20260803T170000Z", "regenerated wording")
            second_head = subprocess.run(
                ["git", "rev-parse", "HEAD"],
                cwd=ring,
                text=True,
                stdout=subprocess.PIPE,
                check=True,
            ).stdout.strip()

            self.assertEqual("committed", first["coordination_commit"]["status"])
            self.assertEqual(
                "no-semantic-change", second["coordination_commit"]["status"]
            )
            self.assertEqual(first_head, second_head)
            self.assertEqual(
                2,
                len(list((ring / ".ring-agent" / "evidence").glob("*/*.md"))),
            )

    def test_inactive_overlapping_task_scope_does_not_block_dispatch(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            ring = Path(temporary) / "ring"
            _configure_task_plans(
                ring,
                pc_scope=["src/**"],
                lp_scope=["src/main/**"],
            )
            state = {
                "decisions": {
                    "PC": {"action": "CONTINUE", "task_id": "task-pc"},
                    "LP": {"action": "HOLD", "task_id": "task-lp"},
                }
            }

            assignments = ring_loop._load_task_assignments(ring, state)

            self.assertTrue(assignments["PC"]["active"])
            self.assertFalse(assignments["LP"]["active"])

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
