#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MODE="${1:-all}"

run_agent_tests() {
  PYTHONPATH="$ROOT/py-ring-agent/src${PYTHONPATH:+:$PYTHONPATH}" \
    python3 -m unittest discover -s py-ring-agent/tests -p 'test_*.py'
  python3 -m compileall -q py-ring-agent
  local script
  for script in scripts/*.sh; do
    [[ -f "$script" ]] || continue
    bash -n "$script"
  done
}

case "$MODE" in
  agents)
    run_agent_tests
    ;;
  unit)
    run_agent_tests
    exec mvn test
    ;;
  integration)
    "$ROOT/scripts/db.sh" test-up
    cleanup() { "$ROOT/scripts/db.sh" test-down >/dev/null 2>&1 || true; }
    trap cleanup EXIT INT TERM
    mvn -Dtest='*IT' -Dspring.profiles.active=test test
    ;;
  all)
    run_agent_tests
    "$ROOT/scripts/db.sh" test-up
    cleanup() { "$ROOT/scripts/db.sh" test-down >/dev/null 2>&1 || true; }
    trap cleanup EXIT INT TERM
    mvn verify
    ;;
  *)
    echo "Usage: $0 {agents|unit|integration|all}" >&2
    exit 2
    ;;
esac
