from __future__ import annotations

import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from zipfile import ZipFile


class ExportEvaluationTest(unittest.TestCase):
    def test_exports_and_removes_an_empty_runtime_snapshot(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            subprocess.run(("git", "init", "-q"), cwd=repo, check=True)
            subprocess.run(
                ("git", "config", "user.email", "test@example.invalid"),
                cwd=repo,
                check=True,
            )
            subprocess.run(
                ("git", "config", "user.name", "Test Exporter"),
                cwd=repo,
                check=True,
            )
            (repo / "README.md").write_text("test\n", encoding="utf-8")
            subprocess.run(("git", "add", "README.md"), cwd=repo, check=True)
            subprocess.run(
                ("git", "commit", "-q", "-m", "baseline"),
                cwd=repo,
                check=True,
            )

            scripts = repo / "scripts"
            scripts.mkdir()
            source_script = (
                Path(__file__).resolve().parents[2]
                / "scripts"
                / "export-evaluation.sh"
            )
            target_script = scripts / "export-evaluation.sh"
            shutil.copy2(source_script, target_script)
            target_script.chmod(0o755)

            run_name = "20260730T235900Z"
            run_dir = repo / "runtime" / "runs" / run_name
            run_dir.mkdir(parents=True)

            result = subprocess.run(
                (str(target_script),),
                cwd=repo,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertFalse(run_dir.exists())
            archive = repo / "runtime" / "runs" / "2026-07-30T23-59-00Z.zip"
            self.assertTrue(archive.is_file())
            package = "r4r-evaluation-2026-07-30T23-59-00Z"
            marker_name = f"{package}/runtime/run/.evaluation-snapshot.json"
            with ZipFile(archive) as zipped:
                self.assertIsNone(zipped.testzip())
                marker = json.loads(zipped.read(marker_name).decode("utf-8"))
            self.assertEqual(run_name, marker["run_name"])
            self.assertEqual(0, marker["source_file_count"])


if __name__ == "__main__":
    unittest.main()
