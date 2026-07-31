#!/usr/bin/env bash
set -Eeuo pipefail

DEST=""
TARGET_ROOT=""
SKIP_PROBE=0

usage() {
  cat <<'TXT'
Uso:
  run-gallery-codex-agent.sh --destination LP|PC [--target-root /ruta]

Sin --target-root, publica en el repositorio donde está instalado el controlador.
TXT
}

while (($#)); do
  case "$1" in
    --destination) DEST="${2:-}"; shift 2 ;;
    --target-root) TARGET_ROOT="${2:-}"; shift 2 ;;
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
TARGET_ROOT="${TARGET_ROOT:-$CONFIG_ROOT}"
TARGET_ROOT="$(realpath "$TARGET_ROOT")"

[[ -f "$TARGET_ROOT/pom.xml" ]] || {
  echo "ERROR: el destino no parece un proyecto Spring/Maven: $TARGET_ROOT" >&2
  exit 2
}

for tool in opencode codex python3 curl git; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "ERROR: falta $tool en el PC coordinador" >&2
    exit 2
  }
done

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
run_dir="$CONFIG_ROOT/runtime/gallery-static/$run_id"
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
    exit 6
  }
fi

task_file="$CONFIG_ROOT/.opencode/commands/task-web-gallery.md"
static_root="$TARGET_ROOT/src/main/resources/static"
mkdir -p "$static_root"

[[ ! -e "$static_root/browser" ]] || {
  echo "ERROR: existe $static_root/browser; retíralo antes de continuar." >&2
  exit 7
}

echo "[r4r-gallery] Codex produce un plan corto, sin explorar el repositorio"
codex exec --sandbox read-only --ephemeral \
  -o "$run_dir/codex-plan.md" - <<EOF
Planifica esta tarea sin navegar por el repositorio y sin usar navegador:
$(cat "$task_file")

Destino exacto: $static_root
Devuelve como máximo 12 líneas. No edites ni ejecutes Git.
EOF

export OPENCODE_CONFIG="$CONFIG_ROOT/opencode.jsonc"
export OPENCODE_CONFIG_DIR="$CONFIG_ROOT/.opencode"
export OPENCODE_CONFIG_CONTENT="$(cat "$CONFIG_ROOT/opencode.jsonc")"

prompt="$(
  cat "$task_file"
  printf '\n\n# Plan obligatorio de Codex\n'
  cat "$run_dir/codex-plan.md"
  printf '\n\nDestino absoluto: %s\n' "$static_root"
  printf 'No leas fuentes locales fuera del directorio static. '
  printf 'No crees browser/. No hagas Git write.\n'
)"

echo "[r4r-gallery] OpenCode ejecuta con $agent"
(
  cd "$TARGET_ROOT"
  opencode run --dir "$TARGET_ROOT" \
    --agent "$agent" --format json --auto "$prompt"
) 2>&1 | tee "$run_dir/opencode.log"

[[ ! -e "$static_root/browser" ]] || {
  echo "ERROR: el agente creó static/browser; ejecución rechazada." >&2
  exit 8
}

for name in \
  galeria-antes-despues.html \
  galeria-antes-despues.css \
  galeria-antes-despues.js
do
  [[ -s "$static_root/$name" ]] || {
    echo "ERROR: falta o está vacío $static_root/$name" >&2
    exit 9
  }
done

if grep -RInE '(^|[/"'\'' ])browser/' \
  "$static_root/galeria-antes-despues."{html,css,js}; then
  echo "ERROR: alguna referencia todavía apunta a browser/." >&2
  exit 10
fi

grep -Fq '/galeria-antes-despues.css' \
  "$static_root/galeria-antes-despues.html"
grep -Fq '/galeria-antes-despues.js' \
  "$static_root/galeria-antes-despues.html"

git -C "$TARGET_ROOT" diff --check -- \
  src/main/resources/static/galeria-antes-despues.html \
  src/main/resources/static/galeria-antes-despues.css \
  src/main/resources/static/galeria-antes-despues.js

echo "[r4r-gallery] Codex revisa únicamente los tres estáticos"
(
  cd "$TARGET_ROOT"
  codex exec --sandbox read-only --ephemeral \
    -o "$run_dir/codex-review.md" - <<EOF
Revisa solo estos tres archivos:
- src/main/resources/static/galeria-antes-despues.html
- src/main/resources/static/galeria-antes-despues.css
- src/main/resources/static/galeria-antes-despues.js

Contrato:
$(cat "$task_file")

Comprueba que no exista ni se referencie browser/. No edites.
Devuelve:
DECISION: ACCEPT | REVISE | BLOCKED
SUMMARY: una frase
DEFECTS: lista breve
EOF
)

echo
echo "Destino: $static_root"
echo "Plan: $run_dir/codex-plan.md"
echo "Log: $run_dir/opencode.log"
echo "Revisión: $run_dir/codex-review.md"
cat "$run_dir/codex-review.md"
