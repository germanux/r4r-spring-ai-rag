#!/usr/bin/env bash
set -Eeuo pipefail

export GIT_AUTHOR_NAME="Codex QWEN3 Agent"
export GIT_AUTHOR_EMAIL="conrado.perez@gmail.com"
export GIT_COMMITTER_NAME="$GIT_AUTHOR_NAME"
export GIT_COMMITTER_EMAIL="$GIT_AUTHOR_EMAIL"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DEST=""
DOCTOR=0
DOCTOR_LOCAL=0
SMOKE=0
SKIP_ENDPOINT=0
FORCE_LP_TASK=0
CONTROLLER_ARGS=()
while (($#)); do
  case "$1" in
    --destination)
      [[ $# -ge 2 ]] || { echo "ERROR: falta valor para --destination" >&2; exit 2; }
      DEST="${2^^}"
      shift 2
      ;;
    --doctor) DOCTOR=1; shift ;;
    --doctor-local) DOCTOR_LOCAL=1; shift ;;
    --smoke) SMOKE=1; shift ;;
    --skip-endpoint-check) SKIP_ENDPOINT=1; shift ;;
    --force-lp-task) FORCE_LP_TASK=1; shift ;;
    *) CONTROLLER_ARGS+=("$1"); shift ;;
  esac
done

case "$DEST" in ""|PC|LP) ;; *) echo "ERROR: usa --destination PC o LP" >&2; exit 2 ;; esac

[[ -f .env ]] || cp .env.example .env
if [[ -n "$DEST" ]]; then
  "$ROOT/scripts/select-r4r-destination.sh" --destination "$DEST" --quiet
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

agent="${R4R_OPENCODE_AGENT:-r4r-pc}"
case "$agent" in
  r4r-pc)
    DEST="PC"
    base_url="${R4R_OPENCODE_PC_BASE_URL:-http://127.0.0.1:11434/v1}"
    model="qwen3-coder-next-80b-t025-168k-8k-pc-pc:latest"
    ;;
  r4r-laptop)
    DEST="LP"
    base_url="${R4R_OPENCODE_LP_BASE_URL:-http://192.168.1.9:11434/v1}"
    model="qwen3-30b-coder-28k-6k-t33:latest"
    ;;
  *) echo "ERROR: agente OpenCode desconocido: $agent" >&2; exit 2 ;;
esac
base_url="${base_url%/}"

mkdir -p runtime/control
RESOLVED_CONFIG="$ROOT/runtime/control/opencode.backend.resolved.json"
python3 - \
  "$ROOT/opencode.jsonc" "$RESOLVED_CONFIG" \
  "${R4R_OPENCODE_PC_BASE_URL:-http://127.0.0.1:11434/v1}" \
  "${R4R_OPENCODE_LP_BASE_URL:-http://192.168.1.9:11434/v1}" \
  "$agent" <<'PY'
import json
from pathlib import Path
from urllib.parse import urlparse
import sys

source, target, pc_url, lp_url, agent = sys.argv[1:]

def normalize(value: str) -> str:
    value = value.rstrip("/")
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise SystemExit(f"Invalid OpenCode base URL: {value!r}")
    if parsed.path.rstrip("/") != "/v1":
        raise SystemExit(f"OpenCode base URL must end in /v1: {value!r}")
    return value

data = json.loads(Path(source).read_text(encoding="utf-8"))
data["default_agent"] = agent
data["provider"]["ollama-pc"]["options"]["baseURL"] = normalize(pc_url)
data["provider"]["ollama-laptop"]["options"]["baseURL"] = normalize(lp_url)
for name, value in data.get("mcp", {}).items():
    if isinstance(value, dict):
        value["enabled"] = bool(name == "codegraph" and agent == "r4r-pc")
Path(target).write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
PY

export OPENCODE_CONFIG="$RESOLVED_CONFIG"
export OPENCODE_CONFIG_DIR="$ROOT/.opencode"
unset OPENCODE_CONFIG_CONTENT || true

python3 - "$RESOLVED_CONFIG" "$ROOT/.opencode/agents/$agent.md" <<'PY'
import json, re, sys
from pathlib import Path
config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
text = Path(sys.argv[2]).read_text(encoding="utf-8")
m = re.search(r"^model:\s*([^/\s]+)/([^\s]+)\s*$", text, re.M)
if not m:
    raise SystemExit("ERROR: el perfil no declara un modelo válido")
provider, model = m.groups()
if provider not in config.get("provider", {}):
    raise SystemExit(f"ERROR: proveedor desconocido en perfil: {provider}")
if model not in config["provider"][provider].get("models", {}):
    raise SystemExit(f"ERROR: modelo del perfil no existe en configuración: {provider}/{model}")
print("OK: configuración OpenCode local resuelta")
PY

if (( DOCTOR_LOCAL )); then
  echo "Agente: $agent"
  echo "Modelo: $model"
  echo "Endpoint configurado: $base_url"
  echo "Config: $RESOLVED_CONFIG"
  [[ "$DEST" == "LP" ]] && echo "Worker LP: directo compacto sin herramientas OpenCode"
  exit 0
fi

if (( ! SKIP_ENDPOINT )) && [[ "${R4R_OPENCODE_ENDPOINT_CHECK:-true}" == "true" ]]; then
  "$ROOT/scripts/probe-r4r-model.sh" \
    --base-url "$base_url" \
    --model "$model" \
    --out "$ROOT/runtime/control/opencode-models-${DEST,,}.json"
fi

if (( DOCTOR )); then
  echo "OK: diagnóstico completo"
  echo "Agente: $agent"
  echo "Modelo: $model"
  echo "Endpoint: $base_url"
  echo "Config: $RESOLVED_CONFIG"
  [[ "$DEST" == "LP" ]] && echo "Worker LP: directo compacto sin herramientas OpenCode"
  exit 0
fi

if (( SMOKE )); then
  if [[ "$DEST" == "LP" ]]; then
    export R4R_REPO="$ROOT"
    export R4R_OPENCODE_LP_BASE_URL="$base_url"
    export R4R_LP_MODEL="$model"
    exec "$ROOT/scripts/opencode-lp-compact.sh" --smoke
  fi
  echo "ERROR: --smoke está definido para el worker LP" >&2
  exit 2
fi

# The active backend Task 04 explicitly belongs to the PC/80B worker. Running the
# laptop worktree against it would duplicate edits and race the PC controller.
if [[ "$DEST" == "LP" && "$FORCE_LP_TASK" -eq 0 ]]; then
  ownership="$({
    python3 - "$ROOT" <<'PY'
import json, sys
from pathlib import Path
root = Path(sys.argv[1])
progress = json.loads((root / '.opencode/progress.json').read_text())
plan = json.loads((root / '.opencode/task-plan.json').read_text())
active = progress.get('active_task')
for item in plan.get('tasks', []):
    if item.get('id') != active:
        continue
    command = root / str(item.get('command'))
    text = command.read_text(encoding='utf-8') if command.is_file() else ''
    delegated = (
        'belongs to the already running PC/80B backend agent' in text
        or 'laptop/gallery agent must not create' in text
    )
    print(json.dumps({'active': active, 'command': str(command.relative_to(root)), 'delegated': delegated}))
    break
else:
    print(json.dumps({'active': active, 'command': None, 'delegated': False}))
PY
  } 2>/dev/null || true)"
  if python3 - "$ownership" <<'PY'
import json, sys
try:
    value=json.loads(sys.argv[1])
except Exception:
    raise SystemExit(1)
raise SystemExit(0 if value.get('delegated') else 1)
PY
  then
    active="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("active"))' "$ownership" 2>/dev/null || true)"
    echo "[r4r] LP_TASK_DELEGATED_TO_PC active_task=${active:-desconocida}"
    echo "El endpoint y el modelo LP están sanos. No se lanza un segundo editor sobre la tarea backend que ya ejecuta el PC."
    echo "Prueba de inferencia LP: ./scripts/run-codex-agent.sh --destination LP --smoke"
    echo "Para una futura tarea expresamente asignada al portátil, el launcher usará el worker compacto directo."
    exit 0
  fi
fi

# Legacy task locks are deliberately disabled.
rm -f runtime/locks/active-task.json

if ! docker info >/dev/null 2>&1; then
  if [[ "${R4R_DOCKER_GROUP_REEXEC:-0}" != "1" ]] \
      && getent group docker >/dev/null 2>&1 \
      && getent group docker | grep -Eq "(^|,)$USER(,|$)"; then
    reexec_args=(--destination "$DEST")
    (( FORCE_LP_TASK )) && reexec_args+=(--force-lp-task)
    reexec_args+=("${CONTROLLER_ARGS[@]}")
    printf -v reexec '%q ' "$0" "${reexec_args[@]}"
    exec sg docker -c "R4R_DOCKER_GROUP_REEXEC=1 ${reexec}"
  fi
  echo "Docker no está disponible sin sudo. Comprueba: docker info" >&2
  exit 2
fi

PYTHON="$ROOT/py-codex-agent/.venv/bin/python"
[[ -x "$PYTHON" ]] || { echo "Ejecuta ./scripts/setup.sh primero" >&2; exit 2; }

if [[ "$DEST" == "LP" ]]; then
  export R4R_REPO="$ROOT"
  export R4R_LP_MODEL="$model"
  export R4R_OPENCODE_LP_BASE_URL="$base_url"
  export R4R_OPENCODE_BIN="$ROOT/scripts/opencode-lp-compact.sh"
  export R4R_COMPACT_LOCAL_WORKER=true
  export R4R_CODEGRAPH_POLICY=off
else
  command -v "${R4R_OPENCODE_BIN:-opencode}" >/dev/null 2>&1 || {
    echo "OpenCode no está en PATH" >&2; exit 2; }
fi
command -v "${R4R_CODEX_BIN:-codex}" >/dev/null 2>&1 || {
  echo "Codex CLI no está en PATH" >&2; exit 2; }

printf '[r4r] agent=%s destination=%s endpoint=%s model=%s worker=%s locks=disabled\n' \
  "$agent" "$DEST" "$base_url" "$model" \
  "$([[ "$DEST" == "LP" ]] && echo compact-direct || echo opencode)"
exec "$PYTHON" -m r4r_codex_agent.cli --repo "$ROOT" "${CONTROLLER_ARGS[@]}"
