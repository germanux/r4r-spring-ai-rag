from __future__ import annotations

from pathlib import Path
import tempfile
import threading
import time
import unittest

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


if __name__ == "__main__":
    unittest.main()
