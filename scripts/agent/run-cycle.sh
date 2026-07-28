#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
[[ -f .env ]] && set -a && source .env && set +a

PYTHON="python3"
[[ -x .venv/bin/python ]] && PYTHON=".venv/bin/python"
PYTHONPATH=tools/orchestrator/src exec "$PYTHON" -m r4r_orchestrator.cli --repo "$ROOT"
