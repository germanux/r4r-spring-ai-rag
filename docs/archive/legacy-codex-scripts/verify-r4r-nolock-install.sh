#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="${R4R_REPO:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ROOT="$(realpath "$ROOT")"
cd "$ROOT"

fail=0

grep -Fq 'Legacy task locks are deliberately disabled.'   scripts/run-codex-agent.sh || {
  echo "FAIL: run-codex-agent.sh sigue siendo antiguo"
  fail=1
}

grep -Fq 'active-task lock control is disabled'   scripts/repair-active-task-lock.sh || {
  echo "FAIL: repair-active-task-lock.sh sigue siendo antiguo"
  fail=1
}

for candidate in   py-codex-agent/src/r4r_codex_agent/runner.py   scripts/run-codex-agent.sh   scripts/repair-active-task-lock.sh   scripts/recover-dirty-worktree.sh
do
  if [[ -f "$candidate" ]]       && grep -Fq 'Active-task lock cannot advance' "$candidate"; then
    echo "FAIL: lógica antigua encontrada en $candidate"
    fail=1
  fi
done

if [[ -e runtime/locks/active-task.json ]]; then
  echo "FAIL: persiste runtime/locks/active-task.json"
  fail=1
fi

echo "R4R_OPENCODE_AGENT=$(grep '^R4R_OPENCODE_AGENT=' .env | cut -d= -f2-)"
echo "R4R_GALLERY_AGENT=$(grep '^R4R_GALLERY_AGENT=' .env | cut -d= -f2-)"

if (( fail )); then
  exit 2
fi

echo "OK: instalación dual-agent sin active-task lock"
