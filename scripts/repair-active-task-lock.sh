#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="${R4R_REPO:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ROOT="$(realpath "$ROOT")"
rm -f "$ROOT/runtime/locks/active-task.json"
echo "[r4r] active-task lock control is disabled; stale lock removed"
