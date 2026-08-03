#!/usr/bin/env bash
set -Eeuo pipefail

# Selector de consolas en vivo para The Ring, PC y LP.
# Uso: ./scripts/show-agent-console.sh [1|2|3]

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"

if [[ "$(basename -- "$SCRIPT_DIR")" == "scripts" ]]; then
  R4R_ROOT="${R4R_ROOT:-$(dirname -- "$SCRIPT_DIR")}"
else
  R4R_ROOT="${R4R_ROOT:-$(git -C "$PWD" rev-parse --show-toplevel 2>/dev/null || pwd -P)}"
fi

RING_SYSTEM_DIR="$R4R_ROOT/runtime/ring-system"
AGENT_RUNTIME_DIR="$R4R_ROOT/runtime/ring-agent"
TAIL_LINES="${R4R_TAIL_LINES:-200}"

usage() {
  cat <<'EOF'
Uso:
  show-agent-console.sh          # muestra el menú
  show-agent-console.sh 1        # RING
  show-agent-console.sh 2        # PC
  show-agent-console.sh 3        # LP

Pulsa Ctrl+C para dejar de seguir la consola.
EOF
}

newest_worker_log() {
  local worker_dir="$1"
  local worker_label="$2"
  local log_file=""

  log_file="$(
    {
      if [[ -d "$AGENT_RUNTIME_DIR/$worker_dir" ]]; then
        find "$AGENT_RUNTIME_DIR/$worker_dir" \
          -type f -name 'controller.console.log' \
          -printf '%T@ %p\n' 2>/dev/null
      fi

      if [[ -d "$AGENT_RUNTIME_DIR/bootstrap" ]]; then
        find "$AGENT_RUNTIME_DIR/bootstrap" \
          -type f -name "*-${worker_label}-hot-sync.log" \
          -printf '%T@ %p\n' 2>/dev/null
        find "$AGENT_RUNTIME_DIR/bootstrap" \
          -type f -name "*-${worker_label}.log" \
          -printf '%T@ %p\n' 2>/dev/null
      fi
    } | sort -nr | head -n 1 | cut -d' ' -f2-
  )"

  [[ -n "$log_file" ]] || return 1
  printf '%s\n' "$log_file"
}

selection="${1:-}"

if [[ -z "$selection" ]]; then
  printf '%s\n' \
    '¿Qué agente quieres ver?' \
    '  1) RING' \
    '  2) PC' \
    '  3) LP'
  read -r -p 'Número [1-3]: ' selection
fi

case "$selection" in
  1)
    label="RING"
    log_file="$RING_SYSTEM_DIR/ring-agent.console.log"
    [[ -f "$log_file" ]] || log_file=""
    ;;
  2)
    label="PC"
    log_file="$(newest_worker_log pc PC)" || true
    ;;
  3)
    label="LP"
    log_file="$(newest_worker_log lp LP)" || true
    ;;
  -h|--help)
    usage
    exit 0
    ;;
  *)
    printf 'Opción no válida: %s\n\n' "$selection" >&2
    usage >&2
    exit 2
    ;;
esac

if [[ -z "${log_file:-}" ]]; then
  printf 'No encuentro una consola disponible para %s.\n' "$label" >&2
  printf 'Estado del sistema:\n  %s/scripts/run-ring-system.sh status\n' "$R4R_ROOT" >&2
  exit 1
fi

printf 'Mostrando %s: %s\n' "$label" "$log_file"
printf 'Pulsa Ctrl+C para salir.\n\n'
exec tail -n "$TAIL_LINES" -F -- "$log_file"
