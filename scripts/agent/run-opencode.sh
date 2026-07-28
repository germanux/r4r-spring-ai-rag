#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
[[ -f .env ]] && set -a && source .env && set +a

OPENCODE_BIN="${R4R_OPENCODE_BIN:-opencode}"
AGENT="${R4R_OPENCODE_AGENT:-r4r-local}"
PROMPT="$(cat .opencode/commands/resume.md)"

exec "$OPENCODE_BIN" --print-logs --log-level INFO run \
  --dir "$ROOT" \
  --agent "$AGENT" \
  --format json \
  "$PROMPT"
