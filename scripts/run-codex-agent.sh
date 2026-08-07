#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

printf '%s\n' \
  '[r4r] DEPRECATED: run-codex-agent.sh delegates to the canonical OpenCode worker.' \
  '[r4r] Use scripts/run-opencode-worker.sh for new automation.' \
  >&2

exec bash "$ROOT/scripts/run-opencode-worker.sh" "$@"
