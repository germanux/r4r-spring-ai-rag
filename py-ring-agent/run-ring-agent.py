#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
sys.path.insert(0, str(HERE / "src"))

from r4r_ring_agent.ring_loop import run_ring_loop  # noqa: E402


# Editable default: True runs one Ring analysis and exits.
RUN_ONCE = False


if __name__ == "__main__":
    raise SystemExit(run_ring_loop(REPO, once=RUN_ONCE))
