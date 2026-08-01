from __future__ import annotations

from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from r4r_ring_agent import ring_loop


class RingLoopTest(unittest.TestCase):
    def test_command_uses_frontier_override(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            command = ring_loop._command(Path(temporary))
            self.assertIn("--agent", command)
            self.assertIn("r4r-ring", command)
            self.assertIn("--model", command)
            self.assertIn("t033-128k", " ".join(command))


if __name__ == "__main__":
    unittest.main()
