#!/usr/bin/env bash
set -Eeuo pipefail

# Selector de consolas en vivo para The Ring, supervisor, PC y LP.
# Uso: ./scripts/show-agent-console.sh [1|2|3|4|all]

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"

if [[ "$(basename -- "$SCRIPT_DIR")" == "scripts" ]]; then
  R4R_ROOT="${R4R_ROOT:-$(dirname -- "$SCRIPT_DIR")}"
else
  R4R_ROOT="${R4R_ROOT:-$(git -C "$PWD" rev-parse --show-toplevel 2>/dev/null || pwd -P)}"
fi

RING_SYSTEM_DIR="$R4R_ROOT/runtime/ring-system"
AGENT_RUNTIME_DIR="$R4R_ROOT/runtime/ring-agent"
TAIL_LINES="${R4R_TAIL_LINES:-200}"
STALE_SECONDS="${R4R_LOG_STALE_SECONDS:-120}"

usage() {
  cat <<'EOF'
Uso:
  show-agent-console.sh          # muestra el menú
  show-agent-console.sh 1        # RING + supervisor
  show-agent-console.sh 2        # PC
  show-agent-console.sh 3        # LP
  show-agent-console.sh 4        # supervisor
  show-agent-console.sh all      # todas las consolas disponibles

Variables opcionales:
  R4R_TAIL_LINES=400             # líneas iniciales por log
  R4R_LOG_STALE_SECONDS=120      # umbral del aviso de inactividad

Pulsa Ctrl+C para dejar de seguir la consola. No detiene los agentes.
EOF
}

newest_worker_log() {
  local worker_dir="$1"
  local worker_label="$2"
  local log_file=""

  log_file="$(
    {
      # Topología actual: el supervisor crea un wrapper por agente en guardian/.
      if [[ -d "$AGENT_RUNTIME_DIR/guardian" ]]; then
        find "$AGENT_RUNTIME_DIR/guardian" \
          -maxdepth 1 -type f -name "*-${worker_label}.log" \
          -printf '%T@ %p\n' 2>/dev/null
      fi

      # Compatibilidad con ejecuciones anteriores.
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

add_log_if_present() {
  local candidate="$1"
  [[ -f "$candidate" ]] || return 0
  log_files+=("$candidate")
}

print_log_state() {
  local file="$1"
  local now modified age size timestamp

  now="$(date +%s)"
  modified="$(stat -c '%Y' -- "$file")"
  age=$((now - modified))
  size="$(stat -c '%s' -- "$file")"
  timestamp="$(stat -c '%y' -- "$file")"

  printf '  %s\n' "$file"
  printf '    última escritura: %s | edad: %ss | tamaño: %s bytes\n' \
    "$timestamp" "$age" "$size"

  if ((size == 0)); then
    printf '    AVISO: el log está vacío.\n'
  elif ((age > STALE_SECONDS)); then
    printf '    AVISO: el log lleva más de %ss sin cambiar; el proceso puede estar esperando o bloqueado.\n' \
      "$STALE_SECONDS"
  fi
}

[[ "$TAIL_LINES" =~ ^[1-9][0-9]*$ ]] || {
  printf 'R4R_TAIL_LINES debe ser un entero positivo: %s\n' "$TAIL_LINES" >&2
  exit 2
}

[[ "$STALE_SECONDS" =~ ^[0-9]+$ ]] || {
  printf 'R4R_LOG_STALE_SECONDS debe ser un entero no negativo: %s\n' "$STALE_SECONDS" >&2
  exit 2
}

selection="${1:-}"

if [[ -z "$selection" ]]; then
  printf '%s\n' \
    '¿Qué consola quieres ver?' \
    '  1) RING + supervisor' \
    '  2) PC' \
    '  3) LP' \
    '  4) Supervisor' \
    '  5) Todas'
  read -r -p 'Número [1-5]: ' selection
fi

declare -a log_files=()

case "$selection" in
  1|ring|RING)
    label="RING + SUPERVISOR"
    add_log_if_present "$RING_SYSTEM_DIR/ring-agent.console.log"
    add_log_if_present "$RING_SYSTEM_DIR/supervisor.log"
    ;;
  2|pc|PC)
    label="PC"
    log_file="$(newest_worker_log pc PC)" || true
    [[ -z "${log_file:-}" ]] || log_files+=("$log_file")
    ;;
  3|lp|LP)
    label="LP"
    log_file="$(newest_worker_log lp LP)" || true
    [[ -z "${log_file:-}" ]] || log_files+=("$log_file")
    ;;
  4|supervisor|SUPERVISOR)
    label="SUPERVISOR"
    add_log_if_present "$RING_SYSTEM_DIR/supervisor.log"
    ;;
  5|all|ALL)
    label="TODAS"
    add_log_if_present "$RING_SYSTEM_DIR/ring-agent.console.log"
    add_log_if_present "$RING_SYSTEM_DIR/supervisor.log"
    log_file="$(newest_worker_log pc PC)" || true
    [[ -z "${log_file:-}" ]] || log_files+=("$log_file")
    log_file="$(newest_worker_log lp LP)" || true
    [[ -z "${log_file:-}" ]] || log_files+=("$log_file")
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

if ((${#log_files[@]} == 0)); then
  printf 'No encuentro una consola disponible para %s.\n' "$label" >&2
  printf 'Comprueba el estado con:\n  %s/scripts/run-ring-system.sh status\n' "$R4R_ROOT" >&2
  exit 1
fi

printf 'Mostrando %s (%s archivo(s)):\n' "$label" "${#log_files[@]}"
for log_file in "${log_files[@]}"; do
  print_log_state "$log_file"
done
printf '\nPulsa Ctrl+C para salir; los agentes seguirán ejecutándose.\n\n'

exec tail -n "$TAIL_LINES" -F -- "${log_files[@]}"
