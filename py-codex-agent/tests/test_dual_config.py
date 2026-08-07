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

    def test_controller_owned_task_commits_are_enabled(self):
        self.assertTrue(self.config["defaults"]["autoCommit"])
        self.assertTrue(self.config["defaults"]["bootstrapCommit"])

    def test_volatile_agent_state_is_ignored(self):
        ignore = (self.repo / ".gitignore").read_text(encoding="utf-8")
        self.assertIn("/.opencode/current/", ignore)
        self.assertIn("/.opencode/memory*.md", ignore)
        self.assertIn("/.ring-agent/state.json", ignore)
