#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MODE="${1:-all}"

case "$MODE" in
  unit)
    exec mvn test
    ;;
  all)
    "$ROOT/scripts/db.sh" test-up
    cleanup() { "$ROOT/scripts/db.sh" test-down >/dev/null 2>&1 || true; }
    trap cleanup EXIT INT TERM
    mvn verify
    ;;
  *)
    echo "Usage: $0 {unit|all}" >&2
    exit 2
    ;;
esac
