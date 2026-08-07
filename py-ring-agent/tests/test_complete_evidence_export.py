from __future__ import annotations

from pathlib import Path
import hashlib
import os
import subprocess
import tempfile
import unittest
import zipfile


SCRIPT = (
    Path(__file__).resolve().parents[2]
    / "scripts"
    / "export-r4r-complete-evidence.sh"
)


class CompleteEvidenceExportTests(unittest.TestCase):
    def test_script_is_directly_executable(self) -> None:
        self.assertTrue(os.access(SCRIPT, os.X_OK))

    def test_exports_unified_layout_as_one_scrubbed_zip(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            repo = base / "ring"
            output = base / "exports"
            repo.mkdir()
            subprocess.run(["git", "init", "-q", str(repo)], check=True)

            files = {
                "runtime/ring-system/supervisor.log": "supervisor alive\n",
                "runtime/ring-agent/ring/run/opencode.console.log": (
                    "OPENAI_API_KEY=plain-secret-value\n"
                ),
                "runtime/control/PC/assignment.json": '{"action":"START"}\n',
                "runtime/the-ring-heartbeats/RING.json": '{"state":"running"}\n',
                ".ring-agent/evidence/task/attempt.md": "durable evidence\n",
                ".opencode/task-plan.json": '{"tasks":[]}\n',
                "config/r4r-agents.json": '{"schemaVersion":2}\n',
                "AGENTS.md": "test instructions\n",
                "runtime/control/PC/.env": "OPENAI_API_KEY=must-not-leak\n",
            }
            for relative, content in files.items():
                path = repo / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")
            for index in range(1, 6):
                session = (
                    repo
                    / "runtime"
                    / "ring-agent"
                    / "ring"
                    / f"session-{index}"
                    / "opencode.console.log"
                )
                session.parent.mkdir(parents=True)
                session.write_text(f"session {index}\n", encoding="utf-8")
                os.utime(session.parent, (index, index))
            for index in range(1, 5):
                evidence = (
                    repo
                    / ".ring-agent"
                    / "evidence"
                    / "task"
                    / f"attempt-{index:02d}.md"
                )
                evidence.write_text(f"attempt {index}\n", encoding="utf-8")
                os.utime(evidence, (index, index))

            completed = subprocess.run(
                ["bash", str(SCRIPT), str(repo), str(output)],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(completed.returncode, 0, completed.stdout)

            archives = list(output.glob("*.zip"))
            self.assertEqual(len(archives), 1, completed.stdout)
            self.assertEqual(list(output.glob("*.sha256")), [])
            self.assertEqual(list(output.glob("*.manifest.txt")), [])

            archive = archives[0]
            with zipfile.ZipFile(archive) as zipped:
                names = zipped.namelist()
                prefix = archive.stem + "/"
                self.assertIn(prefix + "MANIFEST.txt", names)
                self.assertIn(
                    prefix + "runtime/ring-system/supervisor.log", names
                )
                self.assertIn(
                    prefix + "runtime/the-ring-heartbeats/RING.json", names
                )
                self.assertIn(prefix + "diagnostics/processes.txt", names)
                self.assertIn(prefix + "diagnostics/git-topology.txt", names)
                self.assertNotIn(prefix + "runtime/control/PC/.env", names)
                self.assertIn(
                    prefix
                    + "runtime/ring-agent/ring/session-5/opencode.console.log",
                    names,
                )
                self.assertNotIn(
                    prefix
                    + "runtime/ring-agent/ring/session-1/opencode.console.log",
                    names,
                )
                self.assertIn(
                    prefix + ".ring-agent/evidence/task/attempt-04.md",
                    names,
                )
                self.assertNotIn(
                    prefix + ".ring-agent/evidence/task/attempt-01.md",
                    names,
                )

                log = zipped.read(
                    prefix
                    + "runtime/ring-agent/ring/run/opencode.console.log"
                ).decode("utf-8")
                self.assertIn("[REDACTED]", log)
                self.assertNotIn("plain-secret-value", log)

                all_payload = b"".join(
                    zipped.read(name)
                    for name in names
                    if not name.endswith("/")
                )
                self.assertNotIn(b"must-not-leak", all_payload)

            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            self.assertIn(digest, completed.stdout)


if __name__ == "__main__":
    unittest.main()
