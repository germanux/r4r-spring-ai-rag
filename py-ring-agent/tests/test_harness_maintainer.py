from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from r4r_ring_agent import harness_maintainer


class HarnessMaintainerTest(unittest.TestCase):
    def test_command_uses_custom_agent_and_model(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            command = harness_maintainer._command(root, "task")
            self.assertIn("harness-maintainer", command)
            self.assertIn("t033-128k", " ".join(command))

    def test_inline_agent_is_bounded(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            value = json.loads(harness_maintainer._inline_config(Path(temporary)))
            agent = value["agent"]["harness-maintainer"]
            self.assertEqual(agent["mode"], "primary")
            self.assertEqual(agent["steps"], 18)
            self.assertEqual(agent["permission"]["task"], "deny")
            self.assertEqual(agent["permission"]["read"]["*"], "allow")
            self.assertEqual(agent["permission"]["read"]["**/.env.*"], "deny")
            self.assertEqual(agent["permission"]["edit"]["*"], "deny")
            self.assertEqual(agent["permission"]["edit"]["py-ring-agent/**"], "allow")
            self.assertEqual(agent["permission"]["bash"]["*"], "deny")


if __name__ == "__main__":
    unittest.main()
