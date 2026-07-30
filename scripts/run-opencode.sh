#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
[[ -f .env ]] || cp .env.example .env
set -a
# shellcheck disable=SC1091
source .env
set +a
BIN="${R4R_OPENCODE_BIN:-opencode}"
command -v "$BIN" >/dev/null 2>&1 || { echo "OpenCode executable not found: $BIN" >&2; exit 2; }
exec "$BIN" "$@"
