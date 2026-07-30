#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="${R4R_REPO:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ROOT="$(realpath "$ROOT")"
PYTHON="$ROOT/py-codex-agent/.venv/bin/python"

[[ -x "$PYTHON" ]] || {
  echo "Run ./scripts/setup.sh first" >&2
  exit 2
}

exec "$PYTHON" -m r4r_codex_agent.recover_dirty_worktree \
  --repo "$ROOT" \
  "$@"
