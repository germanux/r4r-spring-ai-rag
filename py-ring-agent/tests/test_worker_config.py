from pathlib import Path
import json
import unittest


class DualConfigTest(unittest.TestCase):
    def setUp(self):
        self.repo = Path(__file__).resolve().parents[2]
        self.config = json.loads((self.repo / "config/r4r-agents.json").read_text())

    def test_agents_are_equivalent_fullstack_workers(self):
        pc = set(self.config["agents"]["PC"]["allowedPaths"])
        lp = set(self.config["agents"]["LP"]["allowedPaths"])
        self.assertEqual(pc, lp)
        self.assertIn("frontend/**", pc)
        self.assertIn("src/**", lp)

    def test_agents_use_independent_state_files(self):
        pc = self.config["agents"]["PC"]
        lp = self.config["agents"]["LP"]
        self.assertEqual(pc["plan"], lp["plan"])
        for key in ("progress", "memory", "controlDir"):
            self.assertNotEqual(pc[key], lp[key])

    def test_controller_owned_task_commits_are_enabled(self):
        self.assertTrue(self.config["defaults"]["autoCommit"])
        self.assertFalse(self.config["defaults"]["bootstrapCommit"])

    def test_volatile_agent_state_is_ignored(self):
        ignore = (self.repo / ".gitignore").read_text(encoding="utf-8")
        self.assertIn("/.opencode/current/", ignore)
        self.assertIn("/.opencode/memory*.md", ignore)
        self.assertIn("/.ring-agent/state.json", ignore)
