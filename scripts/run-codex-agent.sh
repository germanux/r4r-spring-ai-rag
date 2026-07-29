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
command -v "${R4R_OPENCODE_BIN:-opencode}" >/dev/null 2>&1 || {
  echo "OpenCode is not installed. Run ./scripts/setup.sh" >&2; exit 2;
}
command -v "${R4R_CODEX_BIN:-codex}" >/dev/null 2>&1 || {
  echo "Codex CLI is not installed. Run ./scripts/setup.sh" >&2; exit 2;
}
exec "$PYTHON" -m r4r_codex_agent.cli --repo "$ROOT" "$@"
