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

if (( ! ALLOW_DIRTY )) && [[ -n "$(git -C "$SOURCE_ROOT" status --porcelain)" ]]; then
  echo "ERROR: el repositorio web tiene cambios; usa --allow-dirty conscientemente" >&2
  git -C "$SOURCE_ROOT" status --short | head -20 >&2
  exit 5
fi

source_match="$(grep -RIl --exclude-dir=.git --exclude-dir=node_modules \
  --exclude-dir=target --exclude-dir=dist --exclude-dir=build \
  -e 'galeria-antes-despues' -e 'Trabajos realizados' "$SOURCE_ROOT" \
  2>/dev/null | head -1 || true)"
[[ -n "$source_match" ]] || {
  echo "ERROR: no encuentro el código local de /galeria-antes-despues" >&2
  exit 4
}

"$CONFIG_ROOT/scripts/select-r4r-destination.sh" --destination "$DEST" --quiet
set -a
# shellcheck disable=SC1091
source "$CONFIG_ROOT/.env"
set +a

if [[ "$DEST" == LP ]]; then
  agent="r4r-gallery-laptop"
else
  agent="r4r-gallery-pc"
fi

export OPENCODE_CONFIG="$CONFIG_ROOT/opencode.jsonc"
export OPENCODE_CONFIG_DIR="$CONFIG_ROOT/.opencode"
mkdir -p "$SOURCE_ROOT/runtime/gallery-agent"
log="$SOURCE_ROOT/runtime/gallery-agent/$(date +%Y%m%d-%H%M%S).log"
prompt="$(cat "$CONFIG_ROOT/.opencode/commands/task-web-gallery.md")"
cd "$SOURCE_ROOT"
opencode run --dir "$SOURCE_ROOT" --agent "$agent" --auto "$prompt" 2>&1 | tee "$log"
printf '\nLog: %s\n' "$log"
