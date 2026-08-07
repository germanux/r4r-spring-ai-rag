#!/usr/bin/env bash
set -Eeuo pipefail

MODE="kill"
OLLAMA_ACTION="restart"
KEEP_BRANCH_SYNC=false
R4R_BASE="${R4R_BASE:-$HOME/Desarrollo}"
TERM_WAIT_SECONDS="${TERM_WAIT_SECONDS:-10}"
KILL_WAIT_SECONDS="${KILL_WAIT_SECONDS:-3}"

usage() {
  cat <<'EOF'
Uso:
  stop-all-r4r-agents.sh
  stop-all-r4r-agents.sh --list
  stop-all-r4r-agents.sh --restart-ollama
  stop-all-r4r-agents.sh --stop-ollama
  stop-all-r4r-agents.sh --keep-models
  stop-all-r4r-agents.sh --keep-branch-sync

Sin argumentos:
  - detiene agentes R4R de todos los worktrees r4r-*.git;
  - detiene unidades systemd --user que puedan relanzarlos;
  - envía SIGTERM y fuerza SIGKILL a cualquier superviviente;
  - termina supervisor Ring, wrappers, controladores Python, OpenCode y descendientes MCP/Node;
  - repite el barrido para impedir reconexiones;
  - descarga los modelos y reinicia ollama.service;
  - verifica que Ollama queda activo y sin modelos cargados.

Opciones:
  --list             Solo muestra objetivos.
  --restart-ollama   Explícito; coincide con el comportamiento por defecto.
  --stop-ollama      Detiene ollama.service tras limpiar clientes.
  --keep-models      No toca Ollama.
  --keep-branch-sync Conserva r4r-agent-branch-sync.service y su timer.
  -h, --help         Ayuda.

Variable:
  R4R_BASE=/home/german/Desarrollo
EOF
}

for arg in "$@"; do
  case "$arg" in
    --list) MODE="list" ;;
    --restart-ollama) OLLAMA_ACTION="restart" ;;
    --stop-ollama) OLLAMA_ACTION="stop" ;;
    --keep-models) OLLAMA_ACTION="keep" ;;
    --keep-branch-sync) KEEP_BRANCH_SYNC=true ;;
    -h|--help) usage; exit 0 ;;
    *) echo "ERROR: opción desconocida: $arg" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -d "$R4R_BASE" ]] || {
  echo "ERROR: no existe R4R_BASE: $R4R_BASE" >&2
  exit 2
}

SELF_PID="$$"
CALLER_PID="$PPID"
SELF_PGID="$(ps -o pgid= -p "$SELF_PID" | tr -d ' ')"
CURRENT_USER="$(id -un)"

declare -A TARGET_PIDS=()
declare -A TARGET_PGIDS=()

section() {
  printf '\n============================================================\n%s\n============================================================\n' "$1"
}

read_cmdline() {
  tr '\0' ' ' < "/proc/$1/cmdline" 2>/dev/null || true
}

read_cwd() {
  readlink -f "/proc/$1/cwd" 2>/dev/null || true
}

is_r4r_path() {
  [[ "$1" == *"$R4R_BASE/r4r-"*".git"* ]]
}

is_target_root() {
  local pid="$1" cmd cwd

  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  [[ "$pid" != "$SELF_PID" ]] || return 1
  [[ "$pid" != "$CALLER_PID" ]] || return 1
  [[ -r "/proc/$pid/cmdline" ]] || return 1

  cmd="$(read_cmdline "$pid")"
  cwd="$(read_cwd "$pid")"

  if ! is_r4r_path "$cmd" && ! is_r4r_path "$cwd"; then
    return 1
  fi

  case "$cmd" in
    *"r4r_worker.cli"*|\
    *"run-opencode-worker.sh"*|\
    *"run-ring-system.py"*|\
    *"run-ring-agent.py"*|\
    *"run-ring-stabilized.py"*|\
    *"run-worker-streamed.py"*|\
    *"run-ring-agent.sh"*|\
    *"run-r4r"*".sh"*|\
    *"ring-agent"*".sh"*|\
    *"opencode run --dir"*|\
    *"opencode.exe run --dir"*|\
    *"opencode-ai/bin/opencode"*" run --dir"*)
      return 0
      ;;
  esac

  return 1
}

add_descendants() {
  local parent="$1" child
  while read -r child; do
    [[ -n "$child" ]] || continue
    [[ "$child" != "$SELF_PID" ]] || continue
    [[ "$child" != "$CALLER_PID" ]] || continue
    if [[ -z "${TARGET_PIDS[$child]+x}" ]]; then
      TARGET_PIDS["$child"]=1
      add_descendants "$child"
    fi
  done < <(pgrep -P "$parent" 2>/dev/null || true)
}

collect_targets() {
  local pid pgid
  TARGET_PIDS=()
  TARGET_PGIDS=()

  while read -r pid; do
    [[ -n "$pid" ]] || continue
    if is_target_root "$pid"; then
      TARGET_PIDS["$pid"]=1
    fi
  done < <(ps -u "$CURRENT_USER" -o pid=)

  for pid in "${!TARGET_PIDS[@]}"; do
    add_descendants "$pid"
  done

  for pid in "${!TARGET_PIDS[@]}"; do
    [[ -d "/proc/$pid" ]] || continue
    pgid="$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ')"
    [[ "$pgid" =~ ^[0-9]+$ ]] || continue
    [[ "$pgid" != "1" ]] || continue
    TARGET_PGIDS["$pgid"]=1
  done
}

print_targets() {
  local pid
  collect_targets

  if ((${#TARGET_PIDS[@]} == 0)); then
    echo "Procesos de agentes R4R: ninguno"
    return
  fi

  printf '%-9s %-9s %-9s %-6s %s\n' PID PPID PGID STAT COMMAND
  for pid in $(printf '%s\n' "${!TARGET_PIDS[@]}" | sort -n); do
    ps -o pid=,ppid=,pgid=,stat=,args= -p "$pid" 2>/dev/null || true
  done
}

unit_matches() {
  local unit="$1"
  if "$KEEP_BRANCH_SYNC"; then
    case "$unit" in
      r4r-agent-branch-sync.service|r4r-agent-branch-sync.timer) return 1 ;;
    esac
  fi
  [[ "$unit" =~ ^r4r-(ring|pc|lp|opencode|agent|worker) ]] ||
  [[ "$unit" =~ ^(ring|pc|lp)-(r4r-)?(agent|worker|opencode) ]] ||
  [[ "$unit" =~ (r4r-ring-agent|r4r-pc-worker|r4r-lp-worker) ]]
}

list_matching_user_units() {
  local unit
  command -v systemctl >/dev/null 2>&1 || return 0

  while read -r unit; do
    [[ -n "$unit" ]] || continue
    if unit_matches "$unit"; then
      printf '%s\n' "$unit"
    fi
  done < <(
    systemctl --user list-units \
      --all \
      --type=service \
      --type=scope \
      --type=timer \
      --no-legend \
      --no-pager 2>/dev/null |
    awk '{print $1}'
  )
}

stop_matching_user_units() {
  local unit
  mapfile -t units < <(list_matching_user_units | sort -u)

  if ((${#units[@]} == 0)); then
    echo "Unidades systemd --user de agentes R4R: ninguna"
    return
  fi

  echo "Deteniendo unidades systemd --user:"
  for unit in "${units[@]}"; do
    printf '  %s\n' "$unit"
  done

  systemctl --user stop "${units[@]}" 2>/dev/null || true
}

signal_targets() {
  local signal="$1" pgid pid

  collect_targets
  ((${#TARGET_PIDS[@]} > 0)) || return 0

  for pgid in "${!TARGET_PGIDS[@]}"; do
    if [[ "$pgid" != "$SELF_PGID" ]]; then
      kill "-$signal" -- "-$pgid" 2>/dev/null || true
    fi
  done

  for pid in "${!TARGET_PIDS[@]}"; do
    [[ "$pid" != "$SELF_PID" ]] || continue
    [[ "$pid" != "$CALLER_PID" ]] || continue
    kill "-$signal" "$pid" 2>/dev/null || true
  done
}

wait_for_targets_to_exit() {
  local timeout="$1" elapsed=0

  while ((elapsed < timeout)); do
    collect_targets
    if ((${#TARGET_PIDS[@]} == 0)); then
      return 0
    fi
    sleep 1
    ((elapsed += 1))
  done

  return 1
}

kill_all_r4r_agents() {
  section "1/5 — UNIDADES QUE PUEDEN RELANZAR AGENTES"
  stop_matching_user_units

  section "2/5 — PROCESOS ANTES DE LA LIMPIEZA"
  print_targets

  collect_targets
  if ((${#TARGET_PIDS[@]} == 0)); then
    echo "No hay procesos R4R que terminar en el primer barrido."
  else
    echo
    echo "Enviando SIGTERM a controladores, OpenCode y descendientes..."
    signal_targets TERM

    if ! wait_for_targets_to_exit "$TERM_WAIT_SECONDS"; then
      echo "Persisten procesos; enviando SIGKILL..."
      signal_targets KILL
      wait_for_targets_to_exit "$KILL_WAIT_SECONDS" || true
    fi
  fi

  section "3/5 — SEGUNDO BARRIDO ANTIRRECONEXIÓN"
  sleep 3
  collect_targets

  if ((${#TARGET_PIDS[@]} > 0)); then
    print_targets
    signal_targets TERM
    sleep 2
    signal_targets KILL
    sleep 1
  else
    echo "No se detectaron reconexiones ni procesos reaparecidos."
  fi
}

list_ollama() {
  if command -v ollama >/dev/null 2>&1; then
    ollama ps 2>/dev/null || true
  else
    echo "Comando ollama no disponible."
  fi
}

unload_ollama_models() {
  local model
  command -v ollama >/dev/null 2>&1 || return 0

  mapfile -t models < <(
    ollama ps 2>/dev/null |
    awk 'NR > 1 && NF > 0 {print $1}'
  )

  if ((${#models[@]} == 0)); then
    echo "Modelos Ollama cargados: ninguno"
    return
  fi

  echo "Descargando modelos Ollama:"
  for model in "${models[@]}"; do
    printf '  %s\n' "$model"
    ollama stop "$model" >/dev/null 2>&1 || true
  done

  sleep 3
}

restart_ollama_service() {
  unload_ollama_models

  if systemctl --user status ollama.service >/dev/null 2>&1; then
    systemctl --user restart ollama.service
  elif systemctl status ollama.service >/dev/null 2>&1; then
    echo "Se solicitará sudo una sola vez para reiniciar ollama.service."
    sudo -v
    sudo systemctl restart ollama.service
  else
    echo "No se encontró ollama.service; solo se descargaron los modelos."
    return
  fi

  sleep 3
}

stop_ollama_service() {
  unload_ollama_models

  if systemctl --user status ollama.service >/dev/null 2>&1; then
    systemctl --user stop ollama.service
  elif systemctl status ollama.service >/dev/null 2>&1; then
    echo "Se solicitará sudo una sola vez para detener ollama.service."
    sudo -v
    sudo systemctl stop ollama.service
  else
    echo "No se encontró ollama.service; solo se descargaron los modelos."
    return
  fi

  sleep 2
}

final_verification() {
  section "5/5 — COMPROBACIÓN FINAL"
  print_targets
  echo
  echo "Estado Ollama:"
  if systemctl --user is-active --quiet ollama.service 2>/dev/null; then
    echo "ollama.service (usuario): active"
  elif systemctl is-active --quiet ollama.service 2>/dev/null; then
    echo "ollama.service (sistema): active"
  else
    echo "ollama.service: no activo o no gestionado por systemd"
  fi
  list_ollama

  collect_targets
  if ((${#TARGET_PIDS[@]} > 0)); then
    echo
    echo "ERROR: todavía quedan procesos R4R activos." >&2
    return 1
  fi

  echo
  echo "Limpieza completa: no quedan controladores, OpenCode ni descendientes R4R."
}

section "CONFIGURACIÓN"
printf 'R4R_BASE:       %s\n' "$R4R_BASE"
printf 'Modo:           %s\n' "$MODE"
printf 'Acción Ollama:  %s\n' "$OLLAMA_ACTION"

if [[ "$MODE" == "list" ]]; then
  section "UNIDADES SYSTEMD --USER DETECTADAS"
  list_matching_user_units || true
  section "PROCESOS R4R DETECTADOS"
  print_targets
  section "OLLAMA"
  list_ollama
  exit 0
fi

kill_all_r4r_agents

section "4/5 — OLLAMA"
case "$OLLAMA_ACTION" in
  unload) unload_ollama_models ;;
  restart) restart_ollama_service ;;
  stop) stop_ollama_service ;;
  keep) echo "Ollama no se modifica (--keep-models)." ;;
esac

final_verification
