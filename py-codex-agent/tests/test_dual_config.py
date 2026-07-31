from pathlib import Path
import json
import unittest


class DualConfigTest(unittest.TestCase):
    def setUp(self):
        self.repo = Path(__file__).resolve().parents[2]
        self.config = json.loads((self.repo / "config/r4r-agents.json").read_text())

    def test_agents_have_disjoint_product_ownership(self):
        pc = set(self.config["agents"]["PC"]["allowedPaths"])
        lp = set(self.config["agents"]["LP"]["allowedPaths"])
        self.assertNotIn("frontend/**", pc)
        self.assertEqual({"frontend/**", "docs/frontend/**"}, lp)

    def test_agents_use_independent_state_files(self):
        pc = self.config["agents"]["PC"]
        lp = self.config["agents"]["LP"]
        for key in ("plan", "progress", "memory", "controlDir"):
            self.assertNotEqual(pc[key], lp[key])

    def test_automatic_commits_are_disabled(self):
        self.assertFalse(self.config["defaults"]["autoCommit"])
        self.assertFalse(self.config["defaults"]["bootstrapCommit"])
