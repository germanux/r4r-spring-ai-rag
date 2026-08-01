#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
sys.path.insert(0, str(HERE / "src"))

from r4r_ring_agent.harness_maintainer import run_maintenance_loop  # noqa: E402


# Editable default. False means one pass every four hours.
RUN_ONCE = False


if __name__ == "__main__":
    raise SystemExit(run_maintenance_loop(REPO, once=RUN_ONCE or "--once" in sys.argv[1:]))
