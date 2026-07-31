#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if (( $# == 0 )); then
  echo "Usage: $0 <maven arguments...>" >&2
  echo "Example: $0 install" >&2
  exit 2
fi

"$ROOT/scripts/db.sh" test-up
cleanup() { "$ROOT/scripts/db.sh" test-down >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM

mvn "$@"
