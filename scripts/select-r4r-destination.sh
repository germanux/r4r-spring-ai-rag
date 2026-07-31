#!/usr/bin/env bash
set -Eeuo pipefail

DEST=""
QUIET=0
while (($#)); do
  case "$1" in
    --destination) DEST="${2:-}"; shift 2 ;;
    --quiet) QUIET=1; shift ;;
    -h|--help) echo "Uso: $0 --destination LP|PC"; exit 0 ;;
    *) echo "ERROR: opción desconocida: $1" >&2; exit 2 ;;
  esac
done

[[ -n "$DEST" ]] || read -r -p "Destino [LP/PC]: " DEST
DEST="${DEST^^}"
case "$DEST" in LP|PC) ;; *) echo "ERROR: destino LP o PC" >&2; exit 2 ;; esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
[[ -f .env ]] || cp .env.example .env

upsert() {
  local file="$1" key="$2" value="$3" tmp
  tmp="$(mktemp)"
  awk -v k="$key" -v v="$value" '
    BEGIN { found=0 }
    $0 ~ "^" k "=" { print k "=" v; found=1; next }
    { print }
    END { if (!found) print k "=" v }
  ' "$file" > "$tmp"
  mv "$tmp" "$file"
}

if [[ "$DEST" == LP ]]; then
  agent="r4r-laptop"
  gallery="r4r-gallery-laptop"
else
  agent="r4r-pc"
  gallery="r4r-gallery-pc"
fi

upsert .env R4R_OPENCODE_AGENT "$agent"
upsert .env R4R_GALLERY_AGENT "$gallery"

python3 - "$agent" <<'PY'
import json
from pathlib import Path
import sys
path = Path("opencode.jsonc")
data = json.loads(path.read_text(encoding="utf-8"))
data["default_agent"] = sys.argv[1]
path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
PY

(( QUIET )) || printf 'Destino=%s\nAgente=%s\nGalería=%s\n' "$DEST" "$agent" "$gallery"
