from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "run-worker-streamed.py"


class WorkerRuntimePathTests(unittest.TestCase):
    def test_environment_overrides_all_authoritative_worktrees(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            values = {
                "R4R_DEVELOPMENT_ROOT": str(base / "development"),
                "R4R_RING_WORKTREE": str(base / "ring"),
                "R4R_PC_WORKTREE": str(base / "pc"),
                "R4R_LP_WORKTREE": str(base / "lp"),
            }
            code = (
                "import importlib.util,json; "
                f"spec=importlib.util.spec_from_file_location('worker_runtime', {str(SCRIPT)!r}); "
                "module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module); "
                "print(json.dumps([str(module.RING_WORKTREE), str(module.PC_WORKTREE), str(module.LP_WORKTREE)]))"
            )
            env = dict(os.environ)
            env.update(values)
            completed = subprocess.run(
                [sys.executable, "-c", code],
                env=env,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )
            self.assertEqual(completed.returncode, 0, completed.stdout)
            self.assertEqual(
                json.loads(completed.stdout),
                [values["R4R_RING_WORKTREE"], values["R4R_PC_WORKTREE"], values["R4R_LP_WORKTREE"]],
            )


if __name__ == "__main__":
    unittest.main()
