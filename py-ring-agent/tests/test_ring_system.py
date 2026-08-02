from __future__ import annotations

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "run-ring-system.py"


class RingSystemTests(unittest.TestCase):
    def test_once_invokes_guardian_and_cleans_pid_file(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            ring = Path(temp)
            scripts = ring / "scripts"
            scripts.mkdir()
            marker = ring / "guardian-ran.txt"
            guardian = scripts / "ensure-r4r-workers.sh"
            guardian.write_text(
                "#!/usr/bin/env bash\n"
                "set -eu\n"
                f"printf ran > {marker!s}\n",
                encoding="utf-8",
            )
            guardian.chmod(0o755)

            completed = subprocess.run(
                [sys.executable, str(SCRIPT), "--once", "--ring", str(ring)],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(completed.returncode, 0, completed.stdout)
            self.assertEqual(marker.read_text(encoding="utf-8"), "ran")
            self.assertFalse((ring / "runtime/ring-system/supervisor.pid").exists())

    def test_once_accepts_guardian_from_another_worktree(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            ring = base / "ring"
            code = base / "code"
            ring.mkdir()
            code.mkdir()
            marker = ring / "external-guardian-ran.txt"
            guardian = code / "ensure-r4r-workers.sh"
            guardian.write_text(
                "#!/usr/bin/env bash\n"
                "set -eu\n"
                f"printf external > {marker!s}\n",
                encoding="utf-8",
            )
            guardian.chmod(0o755)

            completed = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--once",
                    "--ring",
                    str(ring),
                    "--guardian",
                    str(guardian),
                ],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(completed.returncode, 0, completed.stdout)
            self.assertEqual(marker.read_text(encoding="utf-8"), "external")
            self.assertFalse((ring / "runtime/ring-system/supervisor.pid").exists())

    def test_once_forwards_canonical_ring_environment_and_cwd(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            ring = base / "ring"
            code = base / "code"
            ring.mkdir()
            code.mkdir()
            marker = ring / "guardian-context.txt"
            guardian = code / "ensure-r4r-workers.sh"
            guardian.write_text(
                "#!/usr/bin/env bash\n"
                "set -eu\n"
                f"printf '%s\n%s\n' \"$PWD\" \"$R4R_RING_WORKTREE\" > {marker!s}\n",
                encoding="utf-8",
            )
            guardian.chmod(0o755)

            completed = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--once",
                    "--ring",
                    str(ring),
                    "--guardian",
                    str(guardian),
                ],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(completed.returncode, 0, completed.stdout)
            lines = marker.read_text(encoding="utf-8").splitlines()
            self.assertEqual(lines, [str(ring.resolve()), str(ring.resolve())])


if __name__ == "__main__":
    unittest.main()
