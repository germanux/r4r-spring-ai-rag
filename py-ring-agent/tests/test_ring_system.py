from __future__ import annotations

from pathlib import Path
import importlib.util
import signal
import subprocess
import sys
import tempfile
import time
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "run-ring-system.py"


class RingSystemTests(unittest.TestCase):
    def test_startup_invalidates_previous_worker_assignments(self) -> None:
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
            removed = module._invalidate_startup_assignments(ring)
            self.assertEqual(len(removed), 2)
            self.assertFalse(any(path.exists() for path in removed))

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
