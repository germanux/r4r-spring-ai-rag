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


if __name__ == "__main__":
    unittest.main()
