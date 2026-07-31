#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MODE="${1:---all}"

case "$MODE" in
  --all)
    "$ROOT/scripts/run-codex-agent.sh" --destination PC --doctor
    "$ROOT/scripts/run-codex-agent.sh" --destination LP --doctor
    ;;
  --pc) "$ROOT/scripts/run-codex-agent.sh" --destination PC --doctor ;;
  --lp) "$ROOT/scripts/run-codex-agent.sh" --destination LP --doctor ;;
  --local-pc) "$ROOT/scripts/run-codex-agent.sh" --destination PC --doctor-local ;;
  --local-lp) "$ROOT/scripts/run-codex-agent.sh" --destination LP --doctor-local ;;
  *) echo "Uso: $0 [--all|--pc|--lp|--local-pc|--local-lp]" >&2; exit 2 ;;
esac

PYTHON="$ROOT/py-codex-agent/.venv/bin/python"
[[ -x "$PYTHON" ]] || PYTHON=python3
PYTHONPATH="$ROOT/py-codex-agent/src" "$PYTHON" - <<'PY'
from r4r_codex_agent.runner import is_lock_auto_advance_path
required = [
    "r4r-laptop.zip",
    "r4r-spring-ai.zip",
    "install-r4r-agents-stable-v3.1.sh",
    "payload/py-codex-agent/runner.py",
]
failed = [value for value in required if not is_lock_auto_advance_path(value)]
if failed:
    raise SystemExit(f"ERROR: artefactos aún clasificados como producto: {failed}")
print("OK: ZIPs, instaladores y payloads no bloquean dirty resume")
PY
echo "OK: diagnóstico de agentes completado"
