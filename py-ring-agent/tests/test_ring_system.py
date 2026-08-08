from __future__ import annotations

from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import importlib.util
import signal
import shutil
import subprocess
import sys
import tempfile
import time
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "run-ring-system.py"
ROOT = Path(__file__).resolve().parents[2]
GUARDIAN = ROOT / "scripts" / "ensure-r4r-workers.sh"


class RingSystemTests(unittest.TestCase):
    @staticmethod
    def _init_worker_repo(path: Path, branch: str) -> None:
        path.mkdir()
        subprocess.run(
            ["git", "init", "-q", str(path)],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        subprocess.run(
            ["git", "-C", str(path), "symbolic-ref", "HEAD", f"refs/heads/{branch}"],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

    def test_startup_preserves_previous_worker_assignments(self) -> None:
        spec = importlib.util.spec_from_file_location("run_ring_system", SCRIPT)
        self.assertIsNotNone(spec)
        self.assertIsNotNone(spec.loader)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as temp:
            ring = Path(temp)
            for worker in ("PC", "LP"):
                assignment = (
                    ring / "runtime" / "control" / worker / "assignment.json"
                )
                assignment.parent.mkdir(parents=True)
                assignment.write_text("{}\n", encoding="utf-8")
            preserved = module._preserved_startup_assignments(ring)
            self.assertEqual(len(preserved), 2)
            self.assertTrue(all(path.exists() for path in preserved))

    def test_guardian_polling_is_independent_from_ring_review_interval(self) -> None:
        spec = importlib.util.spec_from_file_location("run_ring_system", SCRIPT)
        self.assertIsNotNone(spec)
        self.assertIsNotNone(spec.loader)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)

        self.assertEqual(module.DEFAULT_INTERVAL_SECONDS, 15)

    def test_guardian_dispatches_one_interrupted_recovery_resume(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            ring = base / "ring"
            pc = base / "pc"
            lp = base / "lp"
            ring.mkdir()
            self._init_worker_repo(pc, "agent/pc-qwen3-worker")
            self._init_worker_repo(lp, "agent/laptop-qwen3-worker")

            shutil.copytree(
                ROOT / "py-ring-agent" / "src",
                ring / "py-ring-agent" / "src",
            )
            shutil.copy2(
                ROOT / "py-ring-agent" / "run-worker-streamed.py",
                ring / "py-ring-agent" / "run-worker-streamed.py",
            )

            task_id = "task-fe-03d-dom-state-tests"
            task_plan = ring / ".opencode" / "task-plan.json"
            task_plan.parent.mkdir()
            task_plan.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "tasks": [
                            {
                                "id": task_id,
                                "command": "task.md",
                                "objective": "repair DOM tests",
                                "allowed_paths": ["frontend/**"],
                                "gate": ["true"],
                                "commit_message": "test: repair DOM tests",
                            }
                        ],
                        "final_gate": ["true"],
                    }
                ),
                encoding="utf-8",
            )
            ledger = ring / "runtime" / "control" / "RING" / "global-progress.json"
            ledger.parent.mkdir(parents=True)
            ledger.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "accepted": {},
                        "final_gate": {
                            "status": "PENDING",
                            "checked_at": None,
                            "exit_code": None,
                        },
                        "updated_at": None,
                    }
                ),
                encoding="utf-8",
            )

            authorization_id = "grant-18"
            progress_path = lp / ".opencode" / "progress.lp.json"
            progress_path.parent.mkdir()
            progress = {
                "schema_version": 1,
                "active_task": task_id,
                "tasks": [
                    {
                        "id": task_id,
                        "status": "IN_PROGRESS",
                        "recovery_authorization_consumed": authorization_id,
                        "recovery_grants_total": 1,
                        "recovery_repair_policy_version": 2,
                    }
                ],
            }
            progress_path.write_text(json.dumps(progress), encoding="utf-8")

            now = datetime.now(timezone.utc)
            assignment = ring / "runtime" / "control" / "LP" / "assignment.json"
            assignment.parent.mkdir(parents=True)
            assignment.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "assignment_id": "run-1:LP:task-fe-03d:recovery",
                        "target": "LP",
                        "task_id": task_id,
                        "priority": "advisory",
                        "action": "RETRY_AUTHORIZED",
                        "authorization_id": authorization_id,
                        "recovery_policy_version": 2,
                        "write_scope": ["frontend/**"],
                        "generated_at": now.isoformat(),
                        "expires_at": (now + timedelta(minutes=10)).isoformat(),
                    }
                ),
                encoding="utf-8",
            )

            command = [
                str(GUARDIAN),
                "--once",
                "--check-only",
                "--worker",
                "LP",
                "--ring",
                str(ring),
                "--pc",
                str(pc),
                "--lp",
                str(lp),
            ]
            environment = {**os.environ, "R4R_WORKER_HEARTBEAT_MAX_AGE": "1"}
            resumable = subprocess.run(
                command,
                env=environment,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(1, resumable.returncode, resumable.stdout)
            self.assertIn("LP: inactive", resumable.stdout)
            self.assertNotIn("deliberately quiescent", resumable.stdout)

            progress["tasks"][0]["recovery_resume_count"] = 1
            progress_path.write_text(json.dumps(progress), encoding="utf-8")
            exhausted = subprocess.run(
                command,
                env=environment,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(0, exhausted.returncode, exhausted.stdout)
            self.assertIn("deliberately quiescent", exhausted.stdout)

    def test_once_invokes_guardian_and_cleans_pid_file(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            ring = Path(temp)
            scripts = ring / "scripts"
            scripts.mkdir()
            marker = ring / "guardian-ran.txt"
            guardian = scripts / "ensure-r4r-workers.sh"
            guardian.write_text(
                "#!/usr/bin/env bash\n"
                "set -eu\n"
                f"printf ran > {marker!s}\n",
                encoding="utf-8",
            )
            guardian.chmod(0o755)

            completed = subprocess.run(
                [sys.executable, str(SCRIPT), "--once", "--ring", str(ring)],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(completed.returncode, 0, completed.stdout)
            self.assertEqual(marker.read_text(encoding="utf-8"), "ran")
            self.assertFalse((ring / "runtime/ring-system/supervisor.pid").exists())

    def test_once_accepts_guardian_from_another_worktree(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            ring = base / "ring"
            code = base / "code"
            ring.mkdir()
            code.mkdir()
            marker = ring / "external-guardian-ran.txt"
            guardian = code / "ensure-r4r-workers.sh"
            guardian.write_text(
                "#!/usr/bin/env bash\n"
                "set -eu\n"
                f"printf external > {marker!s}\n",
                encoding="utf-8",
            )
            guardian.chmod(0o755)

            completed = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--once",
                    "--ring",
                    str(ring),
                    "--guardian",
                    str(guardian),
                ],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(completed.returncode, 0, completed.stdout)
            self.assertEqual(marker.read_text(encoding="utf-8"), "external")
            self.assertFalse((ring / "runtime/ring-system/supervisor.pid").exists())

    def test_once_forwards_canonical_ring_environment_and_cwd(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            ring = base / "ring"
            code = base / "code"
            ring.mkdir()
            code.mkdir()
            marker = ring / "guardian-context.txt"
            guardian = code / "ensure-r4r-workers.sh"
            guardian.write_text(
                "#!/usr/bin/env bash\n"
                "set -eu\n"
                f"printf '%s\n%s\n' \"$PWD\" \"$R4R_RING_WORKTREE\" > {marker!s}\n",
                encoding="utf-8",
            )
            guardian.chmod(0o755)

            completed = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--once",
                    "--ring",
                    str(ring),
                    "--guardian",
                    str(guardian),
                ],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(completed.returncode, 0, completed.stdout)
            lines = marker.read_text(encoding="utf-8").splitlines()
            self.assertEqual(lines, [str(ring.resolve()), str(ring.resolve())])

    def test_terminate_managed_process_kills_detached_child_group(self) -> None:
        spec = importlib.util.spec_from_file_location("run_ring_system", SCRIPT)
        self.assertIsNotNone(spec)
        self.assertIsNotNone(spec.loader)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)

        with tempfile.TemporaryDirectory() as temporary:
            pid_file = Path(temporary) / "detached.pid"
            child_code = (
                "import os, signal, time; "
                "signal.signal(signal.SIGTERM, signal.SIG_IGN); "
                f"open({str(pid_file)!r}, 'w').write(str(os.getpid())); "
                "time.sleep(300)"
            )
            parent_code = (
                "import signal, subprocess, sys, time; "
                "signal.signal(signal.SIGTERM, signal.SIG_IGN); "
                f"subprocess.Popen([sys.executable, '-c', {child_code!r}], "
                "start_new_session=True); "
                "time.sleep(300)"
            )
            parent = subprocess.Popen(
                [sys.executable, "-c", parent_code],
                start_new_session=True,
            )
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline and not pid_file.exists():
                time.sleep(0.05)
            self.assertTrue(pid_file.exists())
            child_pid = int(pid_file.read_text(encoding="utf-8"))

            module._terminate_managed_process(parent.pid, timeout_seconds=0.4)
            parent.wait(timeout=3)
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline and Path(
                f"/proc/{child_pid}"
            ).exists():
                time.sleep(0.05)

            self.assertFalse(Path(f"/proc/{parent.pid}").exists())
            self.assertFalse(Path(f"/proc/{child_pid}").exists())



if __name__ == "__main__":
    unittest.main()
