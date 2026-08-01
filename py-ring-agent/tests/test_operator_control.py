from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from r4r_ring_agent.operator_control import RingCommandFile, _strip_jsonc


class OperatorControlTest(unittest.TestCase):
    def test_jsonc_command_is_consumed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            control = RingCommandFile(repo, "RING")
            value = json.loads(_strip_jsonc(control.path.read_text()))
            value["next_state"] = "restart"
            value["target"] = "RING"
            control.path.write_text(json.dumps(value), encoding="utf-8")
            request = control.poll()
            self.assertIsNotNone(request)
            assert request is not None
            control.complete(request, "running", "done")
            final = json.loads(_strip_jsonc(control.path.read_text()))
            self.assertEqual(final["next_state"], "")
            self.assertEqual(final["state"]["RING"], "running")

    def test_maintainer_is_a_supported_target(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            control = RingCommandFile(repo, "MAINTAINER")
            value = json.loads(_strip_jsonc(control.path.read_text()))
            self.assertIn("MAINTAINER", value["state"])

    def test_ring_target_also_reaches_maintainer(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            control = RingCommandFile(repo, "MAINTAINER")
            control.set_state("running", "maintenance pass")
            value = json.loads(_strip_jsonc(control.path.read_text()))
            value["next_state"] = "stop"
            value["target"] = "RING"
            control.path.write_text(json.dumps(value), encoding="utf-8")
            request = control.poll()
            self.assertIsNotNone(request)
            assert request is not None
            self.assertEqual(request.command, "stop")
            self.assertEqual(request.target, "RING")
            control.complete(request, "stopped", "done")
            final = json.loads(_strip_jsonc(control.path.read_text()))
            self.assertEqual(final["next_state"], "")
            self.assertEqual(final["state"]["MAINTAINER"], "stopped")


if __name__ == "__main__":
    unittest.main()
