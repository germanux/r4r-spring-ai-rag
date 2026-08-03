from __future__ import annotations

from pathlib import Path
import os
import signal
import subprocess
import sys
import tempfile
import threading
import time
import unittest

from r4r_ring_agent import ring_process
from r4r_ring_agent.ring_process import run_streamed


class RingProcessTest(unittest.TestCase):
    def test_output_is_persisted_before_exit(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            log = root / "console.log"
            holder = []

            def run() -> None:
                holder.append(
                    run_streamed(
                        ("python3", "-c", "import time; print('first', flush=True); time.sleep(2); print('last', flush=True)"),
                        root,
                        log,
                    )
                )

            thread = threading.Thread(target=run)
            thread.start()
            deadline = time.monotonic() + 1.5
            while time.monotonic() < deadline and (not log.exists() or "first" not in log.read_text()):
                time.sleep(0.05)
            self.assertIn("first", log.read_text())
            self.assertTrue(thread.is_alive())
            thread.join(timeout=5)
            self.assertEqual(holder[0].exit_code, 0)
            self.assertIn("last", log.read_text())

    def test_terminate_process_tree_kills_detached_child_group(self) -> None:
        original_sigint = ring_process.SIGINT_GRACE_SECONDS
        original_sigterm = ring_process.SIGTERM_GRACE_SECONDS
        ring_process.SIGINT_GRACE_SECONDS = 0.3
        ring_process.SIGTERM_GRACE_SECONDS = 0.3
        try:
            with tempfile.TemporaryDirectory() as temporary:
                pid_file = Path(temporary) / "detached.pid"
                child_code = (
                    "import os, signal, time; "
                    "signal.signal(signal.SIGINT, signal.SIG_IGN); "
                    "signal.signal(signal.SIGTERM, signal.SIG_IGN); "
                    f"open({str(pid_file)!r}, 'w').write(str(os.getpid())); "
                    "time.sleep(300)"
                )
                parent_code = (
                    "import signal, subprocess, sys, time; "
                    "signal.signal(signal.SIGINT, signal.SIG_IGN); "
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

                ring_process.terminate_process_tree(parent)
                parent.wait(timeout=3)
                deadline = time.monotonic() + 5
                while time.monotonic() < deadline and Path(
                    f"/proc/{child_pid}"
                ).exists():
                    time.sleep(0.05)

                self.assertFalse(Path(f"/proc/{parent.pid}").exists())
                self.assertFalse(Path(f"/proc/{child_pid}").exists())
        finally:
            ring_process.SIGINT_GRACE_SECONDS = original_sigint
            ring_process.SIGTERM_GRACE_SECONDS = original_sigterm



if __name__ == "__main__":
    unittest.main()
