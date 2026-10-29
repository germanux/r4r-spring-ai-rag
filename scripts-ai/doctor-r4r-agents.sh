#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MODE="${1:---all}"

case "$MODE" in
  --all)
    bash "$ROOT/scripts/run-opencode-worker.sh" --destination PC --doctor
    bash "$ROOT/scripts/run-opencode-worker.sh" --destination LP --doctor
    ;;
  --pc) bash "$ROOT/scripts/run-opencode-worker.sh" --destination PC --doctor ;;
  --lp) bash "$ROOT/scripts/run-opencode-worker.sh" --destination LP --doctor ;;
  --local-pc) bash "$ROOT/scripts/run-opencode-worker.sh" --destination PC --doctor-local ;;
  --local-lp) bash "$ROOT/scripts/run-opencode-worker.sh" --destination LP --doctor-local ;;
  *) echo "Uso: $0 [--all|--pc|--lp|--local-pc|--local-lp]" >&2; exit 2 ;;
esac

PYTHONPATH="$ROOT/py-ring-agent/src" python3 - <<'PY'
from r4r_worker.runner import is_lock_auto_advance_path
required = [
    "r4r-laptop.zip",
    "r4r-spring-ai.zip",
    "install-r4r-agents-stable-v3.1.sh",
    "payload/py-ring-agent/src/r4r_worker/runner.py",
]
failed = [value for value in required if not is_lock_auto_advance_path(value)]
if failed:
    raise SystemExit(f"ERROR: artefactos aún clasificados como producto: {failed}")
print("OK: ZIPs, instaladores y payloads no bloquean dirty resume")
PY
echo "OK: diagnóstico de agentes completado"
