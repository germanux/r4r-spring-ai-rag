#!/usr/bin/env bash
set -euo pipefail
message="${1:-R4R task completed}"
printf '%s\n' "$message"
if command -v notify-send >/dev/null 2>&1; then
  notify-send 'R4R agent' "$message"
fi
