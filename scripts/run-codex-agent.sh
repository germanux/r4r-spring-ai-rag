#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
DEST=""; DOCTOR=0; DOCTOR_LOCAL=0; SMOKE=0; SKIP_ENDPOINT=0; CONTROLLER_ARGS=()
while (($#)); do
  case "$1" in
    --destination) [[ $# -ge 2 ]] || { echo "ERROR: falta valor para --destination" >&2; exit 2; }; DEST="${2^^}"; shift 2 ;;
    --doctor) DOCTOR=1; shift ;;
    --doctor-local) DOCTOR_LOCAL=1; shift ;;
    --smoke) SMOKE=1; shift ;;
    --skip-endpoint-check) SKIP_ENDPOINT=1; shift ;;
    --force-lp-task) echo "AVISO: --force-lp-task ya no es necesario; LP usa su propia cola frontend" >&2; shift ;;
    *) CONTROLLER_ARGS+=("$1"); shift ;;
  esac
done
DEST="${DEST:-${R4R_DESTINATION:-PC}}"; DEST="${DEST^^}"
case "$DEST" in PC|LP) ;; *) echo "ERROR: usa --destination PC o LP" >&2; exit 2 ;; esac

for env_file in .env .env.r4r.local; do
  if [[ -f "$env_file" ]]; then set -a; source "$env_file"; set +a; fi
done
command -v node >/dev/null 2>&1 || { echo "Node.js no está en PATH" >&2; exit 2; }
metadata_path="$(node ./scripts/resolve-r4r-config.mjs --destination "$DEST")"
readarray -t values < <(python3 - "$metadata_path" <<'PY2'
import json,sys
v=json.load(open(sys.argv[1],encoding='utf-8'))
for key in ('agent','model','endpoint','worker','plan','progress','memory','controlDir','opencodeConfig'):
 print(v[key])
print(json.dumps(v.get('peerPaths',[]), separators=(',',':')))
PY2
)
agent="${values[0]}"; model="${values[1]}"; base_url="${values[2]%/}"; worker="${values[3]}"
plan="${values[4]}"; progress="${values[5]}"; memory="${values[6]}"; control_dir="${values[7]}"; resolved_config="${values[8]}"; peer_paths_json="${values[9]}"
export OPENCODE_CONFIG="$ROOT/$resolved_config"
[[ -s "$OPENCODE_CONFIG" ]] || {
  echo "ERROR: configuración resuelta vacía o inexistente: $OPENCODE_CONFIG" >&2
  exit 2
}

# Keep the normal project agent directory, but inject the fully resolved JSON
# as the runtime layer. This avoids relying on OPENCODE_CONFIG_DIR to load a
# second opencode.json, which is inconsistent across OpenCode 1.x releases.
export OPENCODE_CONFIG_DIR="$ROOT/.opencode"
export OPENCODE_CONFIG_CONTENT
OPENCODE_CONFIG_CONTENT="$(cat "$OPENCODE_CONFIG")"
export R4R_WORKER_ID="$DEST" R4R_OPENCODE_AGENT="$agent" R4R_PLAN_DISPLAY="$plan" R4R_MEMORY_PATH="$memory" R4R_PEER_PATHS_JSON="$peer_paths_json"
export R4R_AUTO_COMMIT=false R4R_BOOTSTRAP_COMMIT=false
export R4R_MAX_ATTEMPTS_PER_TASK="${R4R_MAX_ATTEMPTS_PER_TASK:-0}" R4R_MAX_NO_PROGRESS_CYCLES="${R4R_MAX_NO_PROGRESS_CYCLES:-4}" R4R_MAX_TRANSIENT_FAILURES="${R4R_MAX_TRANSIENT_FAILURES:-8}"
export R4R_OPENCODE_BIN="${R4R_OPENCODE_BIN:-opencode}"
command -v "${R4R_OPENCODE_BIN}" >/dev/null 2>&1 || {
  echo "OpenCode no está en PATH" >&2
  exit 2
}

full_model="$(python3 - "$ROOT/.opencode/agents/$agent.md" <<'PYMODEL'
import re
import sys
from pathlib import Path
path = Path(sys.argv[1])
match = re.search(r"^model:\s*([^\s]+)\s*$", path.read_text(encoding="utf-8"), re.MULTILINE)
if match is None:
    raise SystemExit(f"No model declaration in {path}")
print(match.group(1))
PYMODEL
)"

echo "OK: configuración OpenCode resuelta para $DEST"
if (( DOCTOR_LOCAL )); then
  printf 'Agente: %s\nModelo: %s\nEndpoint configurado: %s\nPlan: %s\nProgreso: %s\nConfig: %s\n' "$agent" "$model" "$base_url" "$plan" "$progress" "$ROOT/$resolved_config"
  exit 0
fi
if (( ! SKIP_ENDPOINT )) && [[ "${R4R_OPENCODE_ENDPOINT_CHECK:-true}" == true ]]; then
  ./scripts/probe-r4r-model.sh --base-url "$base_url" --model "$model" --out "$ROOT/$control_dir/models.json"
fi
# First validate the generated JSON itself.
python3 - "$OPENCODE_CONFIG" "$full_model" <<'PYCONFIG'
import json, sys
path, full = sys.argv[1], sys.argv[2]
provider, model = full.split('/', 1)
with open(path, encoding='utf-8') as handle:
    data = json.load(handle)
try:
    data['provider'][provider]['models'][model]
except KeyError as exc:
    raise SystemExit(f"Resolved config does not contain {full}: missing {exc}")
PYCONFIG

provider_id="${full_model%%/*}"
effective_models="$(${R4R_OPENCODE_BIN} --pure models "$provider_id" 2>&1)" || {
  echo "ERROR: OpenCode no pudo cargar el proveedor efectivo: $provider_id" >&2
  printf '%s\n' "$effective_models" >&2
  exit 2
}
# Remove ANSI control sequences before exact comparison.
clean_models="$(printf '%s\n' "$effective_models" | sed -E $'s/\x1B\[[0-9;]*[[:alpha:]]//g' | tr -d '\r')"
if ! grep -Fxq "$full_model" <<<"$clean_models"; then
  echo "ERROR: OpenCode no publica el modelo resuelto: $full_model" >&2
  echo "Config resuelta: $OPENCODE_CONFIG" >&2
  echo "OPENCODE_CONFIG_DIR: $OPENCODE_CONFIG_DIR" >&2
  echo "Salida completa de: opencode models $provider_id" >&2
  printf '%s\n' "$clean_models" >&2
  echo "Diagnóstico recomendado:" >&2
  echo "  OPENCODE_CONFIG='$OPENCODE_CONFIG' OPENCODE_CONFIG_DIR='$OPENCODE_CONFIG_DIR' OPENCODE_CONFIG_CONTENT='...' opencode --pure --print-logs --log-level DEBUG debug config" >&2
  exit 2
fi
echo "OK: modelo visible en OpenCode: $full_model"
if (( DOCTOR )); then
  echo "OK: diagnóstico completo"
  printf 'Agente: %s\nModelo: %s\nEndpoint: %s\nPlan: %s\nProgreso: %s\nConfig: %s\n' "$agent" "$model" "$base_url" "$plan" "$progress" "$ROOT/$resolved_config"
  exit 0
fi
if (( SMOKE )); then
  smoke_output="$(${R4R_OPENCODE_BIN} run \
    --dir "$ROOT" \
    --agent "$agent" \
    --model "$full_model" \
    --format json \
    --auto \
    'Reply exactly: R4R_SMOKE_OK. Do not call any tool.' 2>&1)" || {
    echo "ERROR: la inferencia OpenCode smoke ha fallado" >&2
    printf '%s\n' "$smoke_output" >&2
    exit 1
  }
  printf '%s\n' "$smoke_output"
  grep -Fq 'R4R_SMOKE_OK' <<<"$smoke_output" || {
    echo "ERROR: smoke sin respuesta esperada" >&2
    exit 1
  }
  echo "OK: inferencia OpenCode disponible para $DEST"
  exit 0
fi

if [[ "$DEST" == PC ]]; then
  docker info >/dev/null 2>&1 || { echo "Docker no está disponible para los gates backend" >&2; exit 2; }
fi
command -v "${R4R_CODEX_BIN:-codex}" >/dev/null 2>&1 || { echo "Codex CLI no está en PATH" >&2; exit 2; }
PYTHON="$ROOT/py-codex-agent/.venv/bin/python"
[[ -x "$PYTHON" ]] || { echo "Ejecuta ./scripts/setup.sh primero" >&2; exit 2; }
export R4R_CODEGRAPH_POLICY="${R4R_CODEGRAPH_POLICY:-advisory}" R4R_REQUIRE_CODEGRAPH="${R4R_REQUIRE_CODEGRAPH:-true}"
printf '[r4r] worker=%s agent=%s endpoint=%s model=%s plan=%s auto_commit=false\n' "$DEST" "$agent" "$base_url" "$model" "$plan"
exec "$PYTHON" -m r4r_codex_agent.cli --repo "$ROOT" --plan "$plan" --progress "$progress" "${CONTROLLER_ARGS[@]}"
