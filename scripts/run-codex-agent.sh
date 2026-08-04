#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -r "$ROOT/scripts/r4r-runtime-env.sh" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT/scripts/r4r-runtime-env.sh"
  r4r_runtime_bootstrap "$ROOT"
fi

DEST=""
DOCTOR=0
DOCTOR_LOCAL=0
SMOKE=0
SKIP_ENDPOINT=0
CONTROLLER_ARGS=()

while (($#)); do
  case "$1" in
    --destination)
      [[ $# -ge 2 ]] || { echo "ERROR: falta valor para --destination" >&2; exit 2; }
      DEST="${2^^}"
      shift 2
      ;;
    --doctor)
      DOCTOR=1
      shift
      ;;
    --doctor-local)
      DOCTOR_LOCAL=1
      shift
      ;;
    --smoke)
      SMOKE=1
      shift
      ;;
    --skip-endpoint-check)
      SKIP_ENDPOINT=1
      shift
      ;;
    --force-lp-task)
      echo "AVISO: --force-lp-task ya no es necesario; LP usa su propia cola frontend" >&2
      shift
      ;;
    *)
      CONTROLLER_ARGS+=("$1")
      shift
      ;;
  esac
done

DEST="${DEST:-${R4R_DESTINATION:-PC}}"
DEST="${DEST^^}"
case "$DEST" in
  PC|LP) ;;
  *)
    echo "ERROR: usa --destination PC o LP" >&2
    exit 2
    ;;
esac

for env_file in .env .env.r4r.local; do
  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
  fi
done

NODE_BIN="${R4R_NODE_BIN:-node}"
command -v "$NODE_BIN" >/dev/null 2>&1 || {
  echo "Node.js no está disponible: $NODE_BIN" >&2
  exit 2
}

metadata_path="$("$NODE_BIN" ./scripts/resolve-r4r-config.mjs --destination "$DEST")"

readarray -t values < <(
  python3 - "$metadata_path" <<'PYMETA'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    value = json.load(handle)

for key in (
    "agent",
    "model",
    "endpoint",
    "worker",
    "plan",
    "progress",
    "memory",
    "controlDir",
    "opencodeConfig",
):
    print(value[key])

print(json.dumps(value.get("peerPaths", []), separators=(",", ":")))
runtime = value.get("runtime", {})
for key in (
    "maxAttemptsPerTask",
    "maxNoProgressCycles",
    "maxTransientFailures",
    "autoCommit",
    "bootstrapCommit",
    "checkpointOnGreen",
    "maxSessionSeconds",
    "idleSeconds",
    "maxSessionSteps",
    "repeatEventBudget",
):
    print(json.dumps(runtime[key], separators=(",", ":")))
PYMETA
)

agent="${values[0]}"
model="${values[1]}"
base_url="${values[2]%/}"
worker="${values[3]}"
plan="${values[4]}"
progress="${values[5]}"
memory="${values[6]}"
control_dir="${values[7]}"
resolved_config="${values[8]}"
peer_paths_json="${values[9]}"
runtime_max_attempts="${values[10]}"
runtime_max_no_progress="${values[11]}"
runtime_max_transient="${values[12]}"
runtime_auto_commit="${values[13]}"
runtime_bootstrap_commit="${values[14]}"
runtime_checkpoint_on_green="${values[15]}"
runtime_max_session_seconds="${values[16]}"
runtime_idle_seconds="${values[17]}"
runtime_max_session_steps="${values[18]}"
runtime_repeat_event_budget="${values[19]}"

# Una sola fuente de verdad para OpenCode: opencode.jsonc de la raíz.
unset OPENCODE_CONFIG || true
unset OPENCODE_CONFIG_DIR || true
unset OPENCODE_CONFIG_CONTENT || true

case "$DEST" in
  PC)
    export R4R_GIT_AUTHOR_NAME="${R4R_PC_GIT_AUTHOR_NAME:-GermanGPT PC Agent}"
    export R4R_GIT_AUTHOR_EMAIL="${R4R_PC_GIT_AUTHOR_EMAIL:-germanux@gmail.com}"
    ;;
  LP)
    export R4R_GIT_AUTHOR_NAME="${R4R_LP_GIT_AUTHOR_NAME:-GermanGPT LP Agent}"
    export R4R_GIT_AUTHOR_EMAIL="${R4R_LP_GIT_AUTHOR_EMAIL:-germanux@gmail.com}"
    ;;
esac

for identity_variable in R4R_GIT_AUTHOR_NAME R4R_GIT_AUTHOR_EMAIL; do
  identity_value="${!identity_variable:-}"
  [[ -n "$identity_value" ]] || {
    echo "ERROR: $identity_variable está vacío para $DEST" >&2
    exit 2
  }
  [[ "$identity_value" != *$'\n'* && "$identity_value" != *$'\r'* ]] || {
    echo "ERROR: $identity_variable debe ocupar una sola línea" >&2
    exit 2
  }
done

[[ "$R4R_GIT_AUTHOR_EMAIL" == *@* ]] || {
  echo "ERROR: identidad Git sin email válido: $R4R_GIT_AUTHOR_EMAIL" >&2
  exit 2
}

export R4R_WORKER_ID="$DEST"
export R4R_OPENCODE_AGENT="$agent"
export R4R_PLAN_DISPLAY="$plan"
export R4R_MEMORY_PATH="$memory"
export R4R_PEER_PATHS_JSON="$peer_paths_json"
export R4R_AUTO_COMMIT="${R4R_AUTO_COMMIT:-$runtime_auto_commit}"
export R4R_BOOTSTRAP_COMMIT="${R4R_BOOTSTRAP_COMMIT:-$runtime_bootstrap_commit}"
export R4R_CHECKPOINT_ON_GREEN="${R4R_CHECKPOINT_ON_GREEN:-$runtime_checkpoint_on_green}"
export R4R_MAX_ATTEMPTS_PER_TASK="${R4R_MAX_ATTEMPTS_PER_TASK:-$runtime_max_attempts}"
export R4R_MAX_NO_PROGRESS_CYCLES="${R4R_MAX_NO_PROGRESS_CYCLES:-$runtime_max_no_progress}"
export R4R_MAX_TRANSIENT_FAILURES="${R4R_MAX_TRANSIENT_FAILURES:-$runtime_max_transient}"
export R4R_OPENCODE_MAX_SESSION_SECONDS="${R4R_OPENCODE_MAX_SESSION_SECONDS:-$runtime_max_session_seconds}"
export R4R_OPENCODE_IDLE_SECONDS="${R4R_OPENCODE_IDLE_SECONDS:-$runtime_idle_seconds}"
export R4R_OPENCODE_MAX_SESSION_STEPS="${R4R_OPENCODE_MAX_SESSION_STEPS:-$runtime_max_session_steps}"
export R4R_OPENCODE_REPEAT_EVENT_BUDGET="${R4R_OPENCODE_REPEAT_EVENT_BUDGET:-$runtime_repeat_event_budget}"
export R4R_OPENCODE_BIN="${R4R_OPENCODE_BIN:-opencode}"

command -v "$R4R_OPENCODE_BIN" >/dev/null 2>&1 || {
  echo "OpenCode no está en PATH" >&2
  exit 2
}

full_model="$(
  python3 - "$ROOT/.opencode/agents/$agent.md" <<'PYMODEL'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
match = re.search(
    r"^model:\s*([^\s]+)\s*$",
    path.read_text(encoding="utf-8"),
    re.MULTILINE,
)
if match is None:
    raise SystemExit(f"No model declaration in {path}")
print(match.group(1))
PYMODEL
)"

python3 - "$ROOT/opencode.jsonc" "$full_model" <<'PYCONFIG'
import json
import sys

path, full = sys.argv[1], sys.argv[2]
provider, model = full.split("/", 1)

with open(path, encoding="utf-8") as handle:
    data = json.load(handle)

try:
    data["provider"][provider]["models"][model]
except KeyError as exc:
    raise SystemExit(
        f"opencode.jsonc no contiene {full}; falta la clave {exc}"
    )
PYCONFIG

echo "OK: configuración OpenCode canónica para $DEST"

if (( DOCTOR_LOCAL )); then
  printf 'Agente: %s\nModelo: %s\nEndpoint configurado: %s\nPlan: %s\nProgreso: %s\nAutor Git: %s <%s>\nConfig canónica: %s\nMetadata resuelta: %s\n' \
    "$agent" "$model" "$base_url" "$plan" "$progress" \
    "$R4R_GIT_AUTHOR_NAME" "$R4R_GIT_AUTHOR_EMAIL" \
    "$ROOT/opencode.jsonc" "$ROOT/$resolved_config"
  exit 0
fi

if (( ! SKIP_ENDPOINT )) && [[ "${R4R_OPENCODE_ENDPOINT_CHECK:-true}" == true ]]; then
  ./scripts/probe-r4r-model.sh \
    --base-url "$base_url" \
    --model "$model" \
    --out "$ROOT/$control_dir/models.json"
fi

effective_models="$("$R4R_OPENCODE_BIN" models 2>&1)" || {
  echo "ERROR: OpenCode no pudo cargar el catálogo efectivo" >&2
  printf '%s\n' "$effective_models" >&2
  exit 2
}

clean_models="$(
  printf '%s\n' "$effective_models" \
    | sed -E $'s/\x1B\[[0-9;]*[[:alpha:]]//g' \
    | tr -d '\r'
)"

if ! grep -Fxq "$full_model" <<<"$clean_models"; then
  echo "ERROR: OpenCode no publica el modelo canónico: $full_model" >&2
  echo "Config canónica: $ROOT/opencode.jsonc" >&2
  echo "Salida completa de 'opencode models':" >&2
  printf '%s\n' "$clean_models" >&2
  exit 2
fi

echo "OK: modelo visible en OpenCode: $full_model"

if (( DOCTOR )); then
  echo "OK: diagnóstico completo"
  printf 'Agente: %s\nModelo: %s\nEndpoint: %s\nPlan: %s\nProgreso: %s\nAutor Git: %s <%s>\nConfig: %s\n' \
    "$agent" "$model" "$base_url" "$plan" "$progress" \
    "$R4R_GIT_AUTHOR_NAME" "$R4R_GIT_AUTHOR_EMAIL" "$ROOT/opencode.jsonc"
  exit 0
fi

if (( SMOKE )); then
  smoke_output="$(
    "$R4R_OPENCODE_BIN" run \
      --dir "$ROOT" \
      --agent "$agent" \
      --model "$full_model" \
      --format json \
      --auto \
      'Reply exactly: R4R_SMOKE_OK. Do not call any tool.' \
      2>&1
  )" || {
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

if [[ "$DEST" == "PC" ]]; then
  docker info >/dev/null 2>&1 || {
    echo "Docker no está disponible para los gates backend" >&2
    exit 2
  }
fi

command -v "${R4R_CODEX_BIN:-codex}" >/dev/null 2>&1 || {
  echo "Codex CLI no está en PATH" >&2
  exit 2
}

PYTHON="$ROOT/py-codex-agent/.venv/bin/python"
[[ -x "$PYTHON" ]] || {
  echo "Ejecuta ./scripts/setup.sh primero" >&2
  exit 2
}

# Import the controller package from this worktree.  The virtual environment may
# contain a non-editable installation left behind by an earlier setup run; without
# this, synchronized source fixes can be ignored until the package is reinstalled.
export PYTHONPATH="$ROOT/py-codex-agent/src${PYTHONPATH:+:$PYTHONPATH}"

export R4R_CODEGRAPH_POLICY="${R4R_CODEGRAPH_POLICY:-advisory}"
export R4R_REQUIRE_CODEGRAPH="${R4R_REQUIRE_CODEGRAPH:-true}"

printf '[r4r] worker=%s agent=%s endpoint=%s model=%s plan=%s auto_commit=%s bootstrap_commit=%s git_author=%s<%s>\n' \
  "$DEST" "$agent" "$base_url" "$model" "$plan" \
  "$R4R_AUTO_COMMIT" "$R4R_BOOTSTRAP_COMMIT" \
  "$R4R_GIT_AUTHOR_NAME" "$R4R_GIT_AUTHOR_EMAIL"

exec "$PYTHON" \
  -m r4r_codex_agent.cli \
  --repo "$ROOT" \
  --plan "$plan" \
  --progress "$progress" \
  "${CONTROLLER_ARGS[@]}"
