from pathlib import Path
import json
import tempfile
import unittest
from zipfile import ZipFile

from r4r_codex_agent.diagnostics import build_gate_diagnostics


class DiagnosticsTest(unittest.TestCase):
    def test_packages_compilation_source_and_full_log(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            source = repo / "src/test/java/com/example/BrokenIT.java"
            source.parent.mkdir(parents=True)
            source.write_text("class BrokenIT {}\n", encoding="utf-8")
            evidence = repo / "runtime/runs/test/attempt/evidence"
            stderr = (
                "[ERROR] COMPILATION ERROR :\n"
                f"[ERROR] {source}:[40,25] cannot find symbol\n"
            )

            diagnostics = build_gate_diagnostics(
                repo,
                evidence,
                ("mvn", "verify"),
                1,
                "full stdout",
                stderr,
            )

            self.assertEqual("compilation", diagnostics.classification)
            self.assertEqual(
                ("src/test/java/com/example/BrokenIT.java",),
                diagnostics.source_paths,
            )
            bundle = repo / diagnostics.bundle_path
            self.assertTrue(bundle.is_file())
            with ZipFile(bundle) as archive:
                self.assertIn("gate-full.log", archive.namelist())
                self.assertIn(
                    "files/src/test/java/com/example/BrokenIT.java",
                    archive.namelist(),
                )
            manifest = json.loads((repo / diagnostics.manifest_path).read_text())
            self.assertEqual("compilation", manifest["classification"])

    def test_database_outage_is_not_classified_as_java_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            compose = repo / "docker-postgres/compose.yml"
            compose.parent.mkdir(parents=True)
            compose.write_text("services: {}\n", encoding="utf-8")
            evidence = repo / "runtime/runs/test/attempt/evidence"
            stdout = (
                "Unable to obtain connection from database: "
                "Connection to 127.0.0.1:55433 refused.\nSQL State : 08001\n"
            )

            diagnostics = build_gate_diagnostics(
                repo,
                evidence,
                ("mvn", "install"),
                1,
                stdout,
                "",
            )

            self.assertEqual("database-unavailable", diagnostics.classification)
            self.assertEqual((), diagnostics.source_paths)
            self.assertIn("docker-postgres/compose.yml", diagnostics.related_paths)


if __name__ == "__main__":
    unittest.main()
