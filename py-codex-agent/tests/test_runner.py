from pathlib import Path
import tempfile
import unittest

from r4r_codex_agent.runner import codex_exec_command, path_is_allowed, run_command


class RunnerTest(unittest.TestCase):
    def test_runs_without_shell_and_preserves_exit(self):
        with tempfile.TemporaryDirectory() as directory:
            result = run_command(("python3", "-c", "raise SystemExit(7)"), Path(directory))
        self.assertEqual(7, result.exit_code)

    def test_matches_allowed_paths(self):
        self.assertTrue(path_is_allowed("src/main/App.java", ("src/**",)))
        self.assertFalse(path_is_allowed("scripts/task-gate.sh", ("src/**",)))

    def test_builds_read_only_codex_command(self):
        command = codex_exec_command("codex", Path("schema.json"), Path("out.json"), "gpt-test")
        self.assertIn("read-only", command)
        self.assertIn("--output-schema", command)
        self.assertEqual("-", command[-1])


if __name__ == "__main__":
    unittest.main()
