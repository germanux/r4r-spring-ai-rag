#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

from r4r_ring_agent.ring_stabilization import main

if __name__ == "__main__":
    raise SystemExit(main())
