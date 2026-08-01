#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE_DIR="$ROOT/py-ring-agent/src/r4r_ring_agent"
CURRENT="$MODULE_DIR/ring_loop.py"
LEGACY="$MODULE_DIR/ring_loop_legacy.py"

[[ -f "$LEGACY" ]] || {
  echo "No ring_loop_legacy.py found; nothing to restore." >&2
  exit 1
}
cp "$LEGACY" "$CURRENT"
echo "Restored ring_loop.py from ring_loop_legacy.py. New stabilization files were left in place."
