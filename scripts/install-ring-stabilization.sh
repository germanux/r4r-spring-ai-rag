#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE_DIR="$ROOT/py-ring-agent/src/r4r_ring_agent"
CURRENT="$MODULE_DIR/ring_loop.py"
LEGACY="$MODULE_DIR/ring_loop_legacy.py"
WRAPPER="$ROOT/py-ring-agent/patch-assets/ring_loop.wrapper.py"
AGENT="$ROOT/.opencode/agents/r4r-ring.md"
STAMP="$(date +%Y%m%d-%H%M%S)"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

[[ -f "$CURRENT" ]] || fail "Missing $CURRENT"
[[ -f "$WRAPPER" ]] || fail "Missing $WRAPPER"
[[ -f "$AGENT" ]] || fail "Missing $AGENT"
[[ -f "$MODULE_DIR/ring_stabilization.py" ]] || fail "Missing ring_stabilization.py"

grep -q '^mode: primary$' "$AGENT" || fail "r4r-ring agent is not primary"
grep -q '^  external_directory: deny$' "$AGENT" || fail "external_directory must remain deny"

if grep -q 'R4R_RING_STABILIZED_WRAPPER' "$CURRENT"; then
  printf 'Ring stabilization wrapper is already installed.\n'
else
  if [[ -e "$LEGACY" ]]; then
    cp -a "$LEGACY" "$MODULE_DIR/ring_loop_legacy.py.backup-$STAMP"
  fi
  cp -a "$CURRENT" "$LEGACY"
  cp -a "$CURRENT" "$MODULE_DIR/ring_loop.py.backup-$STAMP"
  cp "$WRAPPER" "$CURRENT"
fi

chmod +x "$ROOT/py-ring-agent/run-ring-stabilized.py"

PYTHONPATH="$ROOT/py-ring-agent/src" python3 -m compileall -q "$ROOT/py-ring-agent/src"
PYTHONPATH="$ROOT/py-ring-agent/src" python3 -m unittest discover \
  -s "$ROOT/py-ring-agent/tests" \
  -p 'test_ring_stabilization.py' \
  -v

printf '\nOpenCode agent check:\n'
opencode agent list | grep -F 'r4r-ring (primary)' \
  || fail "OpenCode does not expose r4r-ring as a primary agent"

printf '\nInstalled without touching Git history or the PC/LP worktrees.\n'
printf 'One-shot command:\n  ./py-ring-agent/run-ring-stabilized.py --once\n'
