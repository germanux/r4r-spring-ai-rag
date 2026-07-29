#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
[[ -f .env ]] || cp .env.example .env
set -a
# shellcheck disable=SC1091
source .env
set +a

PYTHON="$ROOT/py-codex-agent/.venv/bin/python"
[[ -x "$PYTHON" ]] || { echo "Run ./scripts/setup.sh first" >&2; exit 2; }
exec "$PYTHON" -m r4r_codex_agent.cli --repo "$ROOT" "$@"
