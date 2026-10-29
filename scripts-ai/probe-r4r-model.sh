#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL=""
MODEL=""
OUT=""
CONNECT_TIMEOUT=5
MAX_TIME=45
QUIET=0

usage() {
  cat <<'TXT'
Uso:
  probe-r4r-model.sh --base-url URL --model MODELO [--out FICHERO] [--quiet]
TXT
}

while (($#)); do
  case "$1" in
    --base-url) BASE_URL="${2:-}"; shift 2 ;;
    --model) MODEL="${2:-}"; shift 2 ;;
    --out) OUT="${2:-}"; shift 2 ;;
    --connect-timeout) CONNECT_TIMEOUT="${2:-}"; shift 2 ;;
    --max-time) MAX_TIME="${2:-}"; shift 2 ;;
    --quiet) QUIET=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "ERROR: opción desconocida: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -n "$BASE_URL" && -n "$MODEL" ]] || {
  echo "ERROR: faltan --base-url o --model" >&2
  exit 2
}
BASE_URL="${BASE_URL%/}"
python3 - "$BASE_URL" <<'PY'
from urllib.parse import urlparse
import sys
value = sys.argv[1]
parsed = urlparse(value)
if parsed.scheme not in {"http", "https"} or not parsed.netloc:
    raise SystemExit(f"ERROR: URL no absoluta: {value!r}")
if parsed.path.rstrip("/") != "/v1":
    raise SystemExit(f"ERROR: la URL debe terminar en /v1: {value!r}")
PY

[[ -n "$OUT" ]] || OUT="$(mktemp)"
mkdir -p "$(dirname "$OUT")"

set +e
http="$(
  curl -sS --connect-timeout "$CONNECT_TIMEOUT" --max-time "$MAX_TIME" \
    -o "$OUT" -w '%{http_code}' "$BASE_URL/models"
)"
curl_rc=$?
set -e

if (( curl_rc != 0 )) || [[ "$http" != "200" ]]; then
  host_port="$(
    python3 - "$BASE_URL" <<'PY'
from urllib.parse import urlparse
import sys
p=urlparse(sys.argv[1])
print(f"{p.hostname}:{p.port or (443 if p.scheme == 'https' else 80)}")
PY
  )"
  echo "ERROR: no se puede consultar $BASE_URL/models" >&2
  echo "curl_exit=$curl_rc HTTP=${http:-000} destino=$host_port" >&2
  echo "La configuración local está instalada, pero el servicio Ollama remoto/local no está accesible." >&2
  echo "Comprueba equipo encendido, IP, puerto 11434, firewall y que Ollama escuche fuera de 127.0.0.1." >&2
  [[ -s "$OUT" ]] && cat "$OUT" >&2
  exit 6
fi

actual="$(
  python3 - "$OUT" "$MODEL" <<'PY'
import json
from pathlib import Path
import sys

payload = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
expected = sys.argv[2]

def values():
    raw = payload.get("data")
    if not isinstance(raw, list):
        raw = payload.get("models")
    if not isinstance(raw, list):
        return
    for item in raw:
        if not isinstance(item, dict):
            continue
        for key in ("id", "name", "model"):
            value = item.get(key)
            if isinstance(value, str) and value:
                yield value
                break

ids = sorted(set(values() or []))

def without_latest(value: str) -> str:
    return value[:-7] if value.endswith(":latest") else value

matches = [value for value in ids
           if value == expected or without_latest(value) == without_latest(expected)]
if not matches:
    raise SystemExit(
        "ERROR: el endpoint responde, pero no publica el modelo esperado "
        f"{expected!r}. Modelos: {ids}"
    )
print(matches[0])
PY
)"

(( QUIET )) || {
  echo "OK: endpoint HTTP 200"
  echo "Modelo solicitado: $MODEL"
  echo "Modelo publicado: $actual"
}
