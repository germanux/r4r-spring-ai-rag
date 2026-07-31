#!/usr/bin/env bash
set -Eeuo pipefail
DEST=""
SOURCE_ROOT=""
ALLOW_DIRTY=0
while (($#)); do
  case "$1" in
    --destination) DEST="${2:-}"; shift 2 ;;
    --source-root) SOURCE_ROOT="${2:-}"; shift 2 ;;
    --allow-dirty) ALLOW_DIRTY=1; shift ;;
    -h|--help)
      echo "Uso: $0 --destination LP|PC --source-root /ruta/web [--allow-dirty]"
      exit 0
      ;;
    *) echo "ERROR: opción desconocida: $1" >&2; exit 2 ;;
  esac
done
[[ -n "$DEST" ]] || read -r -p "Destino [LP/PC]: " DEST
DEST="${DEST^^}"
case "$DEST" in LP|PC) ;; *) echo "ERROR: destino LP o PC" >&2; exit 2 ;; esac
CONFIG_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_ROOT="${SOURCE_ROOT:-$CONFIG_ROOT}"
SOURCE_ROOT="$(cd "$SOURCE_ROOT" && pwd)"
[[ -d "$SOURCE_ROOT/.git" || -f "$SOURCE_ROOT/.git" ]] || {
  echo "ERROR: --source-root no es un worktree Git" >&2; exit 2; }
if ps -eo args= | grep -E '[r]4r_codex_agent|[o]pencode run' | grep -F "$SOURCE_ROOT" >/dev/null; then
  echo "ERROR: ya hay un agente activo en el source worktree" >&2; exit 3
fi
command -v opencode >/dev/null 2>&1 || {
  echo "ERROR: OpenCode no está instalado o no está en PATH." >&2; exit 2; }
if (( ! ALLOW_DIRTY )) && [[ -n "$(git -C "$SOURCE_ROOT" status --porcelain)" ]]; then
  echo "ERROR: el source worktree tiene cambios; usa uno limpio." >&2
  git -C "$SOURCE_ROOT" status --short | head -20 >&2
  echo "Solo continúa conscientemente con --allow-dirty." >&2
  exit 5
fi
source_match="$(
  while IFS= read -r -d '' candidate; do
    if grep -Iq -e 'galeria-antes-despues' -e 'Trabajos realizados' "$candidate"; then
      printf '%s\n' "$candidate"
      break
    fi
  done < <(
    find "$SOURCE_ROOT" \
      -type d \
        \( -name .git -o -name .opencode -o -name node_modules -o -name target \
           -o -name runtime -o -name patches-applied -o -name dist -o -name build \) \
        -prune -o \
      -type f \
        \( -name '*.html' -o -name '*.htm' -o -name '*.css' -o -name '*.scss' \
           -o -name '*.js' -o -name '*.mjs' -o -name '*.cjs' -o -name '*.ts' \
           -o -name '*.tsx' -o -name '*.jsx' -o -name '*.vue' -o -name '*.astro' \
           -o -name '*.svelte' -o -name '*.md' -o -name '*.mdx' -o -name '*.json' \) \
        ! -name 'AGENTS.md' -print0
  )
)"
if [[ -z "$source_match" ]]; then
  echo "ERROR: no encuentro el código local de /galeria-antes-despues." >&2
  echo "Playwright puede leer la web pública, pero no modificar su servidor." >&2
  echo "Indica el repositorio real mediante --source-root." >&2
  exit 4
fi
"$CONFIG_ROOT/scripts/select-r4r-destination.sh" --destination "$DEST" --quiet
set -a
source "$CONFIG_ROOT/.env"
set +a
if [[ "$DEST" == LP ]]; then agent="r4r-gallery-laptop"; else agent="r4r-gallery-pc"; fi
export OPENCODE_CONFIG="$CONFIG_ROOT/opencode.jsonc"
export OPENCODE_CONFIG_DIR="$CONFIG_ROOT/.opencode"
export OPENCODE_CONFIG_CONTENT="$(cat "$CONFIG_ROOT/opencode.jsonc")"
mkdir -p "$SOURCE_ROOT/runtime/gallery-agent"
log="$SOURCE_ROOT/runtime/gallery-agent/$(date +%Y%m%d-%H%M%S).log"
prompt="$(cat "$CONFIG_ROOT/.opencode/commands/task-web-gallery.md")"
cd "$SOURCE_ROOT"
opencode run --dir "$SOURCE_ROOT" --agent "$agent" --auto "$prompt" 2>&1 | tee "$log"
printf '\nLog: %s\n' "$log"
