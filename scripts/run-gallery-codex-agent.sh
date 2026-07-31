#!/usr/bin/env bash
set -Eeuo pipefail

DEST=""
SOURCE_ROOT=""
SOURCE_URL=""
CLONE_DIR="${HOME}/Desarrollo/r4r-gallery-web-agent.git"
ALLOW_DIRTY=0
SKIP_PROBE=0

usage() {
  cat <<'TXT'
Uso:
  run-gallery-codex-agent.sh --destination LP|PC --source-root /ruta/local
  run-gallery-codex-agent.sh --destination LP|PC --source-url URL [--clone-dir RUTA]

Opciones:
  --allow-dirty  Permite trabajar sobre un repositorio con cambios.
  --skip-probe   Omite la prueba mínima del modelo seleccionado.
TXT
}

while (($#)); do
  case "$1" in
    --destination) DEST="${2:-}"; shift 2 ;;
    --source-root) SOURCE_ROOT="${2:-}"; shift 2 ;;
    --source-url) SOURCE_URL="${2:-}"; shift 2 ;;
    --clone-dir) CLONE_DIR="${2:-}"; shift 2 ;;
    --allow-dirty) ALLOW_DIRTY=1; shift ;;
    --skip-probe) SKIP_PROBE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "ERROR: opción desconocida: $1" >&2; usage >&2; exit 2 ;;
  esac
done

DEST="${DEST^^}"
case "$DEST" in
  LP|PC) ;;
  *) echo "ERROR: usa --destination LP o PC" >&2; exit 2 ;;
esac

CONFIG_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$CONFIG_ROOT"

for tool in git opencode codex python3 curl; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "ERROR: falta $tool en el PC coordinador" >&2
    exit 2
  }
done

if [[ -n "$SOURCE_ROOT" && "$SOURCE_ROOT" =~ ^https?:// ]]; then
  echo "ERROR: --source-root requiere una ruta local." >&2
  echo "Usa --source-url para clonar una URL." >&2
  exit 2
fi

if [[ -n "$SOURCE_URL" ]]; then
  if [[ ! -e "$CLONE_DIR" ]]; then
    git clone "$SOURCE_URL" "$CLONE_DIR"
  fi
  SOURCE_ROOT="$CLONE_DIR"
fi

[[ -n "$SOURCE_ROOT" ]] || {
  echo "ERROR: indica --source-root o --source-url" >&2
  exit 2
}
SOURCE_ROOT="$(realpath "$SOURCE_ROOT")"
git -C "$SOURCE_ROOT" rev-parse --show-toplevel >/dev/null

if (( ! ALLOW_DIRTY )) \
    && [[ -n "$(git -C "$SOURCE_ROOT" status --porcelain)" ]]; then
  echo "ERROR: el repositorio web tiene cambios." >&2
  git -C "$SOURCE_ROOT" status --short | head -n 24 >&2
  echo "Usa un worktree limpio o --allow-dirty conscientemente." >&2
  exit 5
fi

"$CONFIG_ROOT/scripts/select-r4r-destination.sh" \
  --destination "$DEST" --quiet

set -a
source "$CONFIG_ROOT/.env"
set +a

if [[ "$DEST" == LP ]]; then
  agent="r4r-gallery-laptop"
  base_url="$R4R_OPENCODE_LP_BASE_URL"
  model="qwen3-30b-coder-28k-6k-t33:latest"
else
  agent="r4r-gallery-pc"
  base_url="$R4R_OPENCODE_PC_BASE_URL"
  model="qwen3-coder-next-80b-t025-168k-8k-pc-pc"
fi

run_id="$(date -u +%Y%m%dT%H%M%SZ)"
run_dir="$CONFIG_ROOT/runtime/gallery-codex/$run_id"
mkdir -p "$run_dir"

if (( ! SKIP_PROBE )); then
  echo "[r4r-gallery] comprobando $agent"
  http="$(
    curl -sS --connect-timeout 5 --max-time 600 \
      -o "$run_dir/model-probe.json" -w '%{http_code}' \
      "${base_url%/}/chat/completions" \
      -H 'Content-Type: application/json' \
      -d "{
        \"model\":\"$model\",
        \"messages\":[{\"role\":\"user\",\"content\":\"Reply exactly LP_OK\"}],
        \"max_tokens\":16,
        \"stream\":false
      }"
  )"
  [[ "$http" == 200 ]] || {
    echo "ERROR: prueba del modelo devolvió HTTP=$http" >&2
    cat "$run_dir/model-probe.json" >&2
    exit 6
  }
  python3 - "$run_dir/model-probe.json" <<'PY'
import json, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
print("[r4r-gallery] modelo:", data["choices"][0]["message"]["content"])
PY
fi

task_file="$CONFIG_ROOT/.opencode/commands/task-web-gallery.md"
[[ -f "$task_file" ]] || {
  echo "ERROR: falta $task_file" >&2
  exit 2
}

echo "[r4r-gallery] Codex planifica en modo read-only"
(
  cd "$SOURCE_ROOT"
  codex exec --sandbox read-only --ephemeral \
    -o "$run_dir/codex-plan.md" - <<EOF
Actúa como arquitecto/revisor principal. Planifica una única modificación acotada
del repositorio local $SOURCE_ROOT.

Objetivo:
$(cat "$task_file")

Inspecciona el repositorio local. No edites. No hagas Git write, push ni despliegue.
Devuelve un plan corto con:
1. archivos exactos;
2. cambios mínimos;
3. validaciones locales y Playwright;
4. riesgos y límites.
EOF
)

export OPENCODE_CONFIG="$CONFIG_ROOT/opencode.jsonc"
export OPENCODE_CONFIG_DIR="$CONFIG_ROOT/.opencode"
export OPENCODE_CONFIG_CONTENT="$(cat "$CONFIG_ROOT/opencode.jsonc")"

prompt="$(
  cat "$task_file"
  printf '\n\n# Plan obligatorio de Codex\n'
  cat "$run_dir/codex-plan.md"
  printf '\n\nEdita solo el repositorio local. '
  printf 'No hagas commit, push ni despliegue. '
  printf 'Ejecuta validaciones acotadas y detente.\n'
)"

echo "[r4r-gallery] OpenCode ejecuta con $agent"
(
  cd "$SOURCE_ROOT"
  opencode run --dir "$SOURCE_ROOT" \
    --agent "$agent" --format json --auto "$prompt"
) 2>&1 | tee "$run_dir/opencode.log"

git -C "$SOURCE_ROOT" diff --check

echo "[r4r-gallery] Codex revisa el resultado"
(
  cd "$SOURCE_ROOT"
  codex exec --sandbox read-only --ephemeral \
    -o "$run_dir/codex-review.md" - <<EOF
Revisa en modo read-only los cambios actuales del repositorio $SOURCE_ROOT.

Objetivo:
$(cat "$task_file")

Plan previo:
$(cat "$run_dir/codex-plan.md")

Inspecciona git diff, archivos modificados y pruebas disponibles. No edites ni hagas
Git write. Devuelve exactamente:
DECISION: ACCEPT | REVISE | BLOCKED
SUMMARY: una frase
DEFECTS: lista concreta
NEXT_ACTION: una sola acción acotada
EOF
)

echo
echo "Plan:    $run_dir/codex-plan.md"
echo "OpenCode: $run_dir/opencode.log"
echo "Review:  $run_dir/codex-review.md"
echo
cat "$run_dir/codex-review.md"
