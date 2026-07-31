#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="${R4R_REPO:-$SCRIPT_ROOT}"
REPO="$(realpath "$REPO")"
MODE="${1:---all}"
SELF_PID="$$"

usage() {
  cat <<'EOF'
Uso:
  ./scripts/find-and-stop-r4r-orphans.sh
  ./scripts/find-and-stop-r4r-orphans.sh --all
  ./scripts/find-and-stop-r4r-orphans.sh --list
  ./scripts/find-and-stop-r4r-orphans.sh --kill
  ./scripts/find-and-stop-r4r-orphans.sh --restart-ollama

Sin argumentos ejecuta el ciclo completo:
  1. lista procesos R4R/OpenCode del repositorio;
  2. termina controladores y descendientes;
  3. fuerza la salida de supervivientes;
  4. reinicia Ollama;
  5. espera a que Ollama quede activo;
  6. comprueba el estado final.

Variable opcional:
  R4R_REPO=/ruta/al/repositorio
EOF
}

case "$MODE" in
  --all|--list|--kill|--restart-ollama) ;;
  -h|--help)
    usage
    exit 0
    ;;
  *)
    echo "ERROR: modo no reconocido: $MODE" >&2
    usage >&2
    exit 2
    ;;
esac

if [[ ! -d "$REPO" ]]; then
  echo "ERROR: no existe el repositorio: $REPO" >&2
  exit 2
fi

declare -A selected=()

read_cmdline() {
  local pid="$1"
  tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true
}

read_cwd() {
  local pid="$1"
  readlink -f "/proc/$pid/cwd" 2>/dev/null || true
}

belongs_to_repo() {
  local pid="$1"
  local cmd cwd

  cmd="$(read_cmdline "$pid")"
  cwd="$(read_cwd "$pid")"

  [[ "$cmd" == *"$REPO"* || "$cwd" == "$REPO"* ]]
}

is_r4r_root_process() {
  local pid="$1"
  local cmd

  [[ "$pid" != "$SELF_PID" ]] || return 1
  [[ -r "/proc/$pid/cmdline" ]] || return 1

  cmd="$(read_cmdline "$pid")"

  # Controlador Python o launcher Bash asociado al repositorio.
  if [[ "$cmd" == *"r4r_codex_agent"* || "$cmd" == *"run-codex-agent.sh"* ]]; then
    belongs_to_repo "$pid"
    return
  fi

  # Solo OpenCode lanzado explícitamente con --dir apuntando a ESTE repositorio.
  # No captura "opencode acp" ni sesiones de otros repositorios.
  if [[ "$cmd" == *"opencode run"* && "$cmd" == *"--dir $REPO"* ]]; then
    return 0
  fi

  return 1
}

refresh_selection() {
  local proc pid ppid changed

  selected=()

  for proc in /proc/[0-9]*; do
    pid="${proc##*/}"
    if is_r4r_root_process "$pid"; then
      selected["$pid"]=1
    fi
  done

  # Añadir descendientes transitivos: Playwright MCP, CodeGraph, watchdogs, etc.
  changed=1
  while (( changed )); do
    changed=0
    while read -r pid ppid; do
      [[ "$pid" != "$SELF_PID" ]] || continue
      [[ -n "${selected[$ppid]:-}" ]] || continue
      if [[ -z "${selected[$pid]:-}" ]]; then
        selected["$pid"]=1
        changed=1
      fi
    done < <(ps -e -o pid=,ppid=)
  done
}

print_r4r_processes() {
  local pid

  refresh_selection

  echo
  echo "Procesos R4R/OpenCode asociados a:"
  echo "  $REPO"

  if (( ${#selected[@]} == 0 )); then
    echo "  ninguno"
    return 0
  fi

  printf '%-8s %-8s %-8s %-9s %-6s %s\n' \
    PID PPID PGID ELAPSED STAT COMMAND

  for pid in "${!selected[@]}"; do
    ps -ww -p "$pid" \
      -o pid=,ppid=,pgid=,etimes=,stat=,args= \
      2>/dev/null || true
  done | sort -n
}

print_ollama_processes() {
  echo
  echo "Procesos Ollama/modelo:"
  ps -ww -eo pid=,ppid=,pgid=,etimes=,stat=,args= \
    | grep -E '[o]llama( serve)?|[l]lama-server' \
    || echo "  ninguno"

  if command -v ollama >/dev/null 2>&1; then
    echo
    echo "Modelos cargados según 'ollama ps':"
    ollama ps 2>/dev/null || echo "  Ollama no responde"
  fi
}

show_snapshot() {
  local title="$1"

  echo
  echo "============================================================"
  echo "$title"
  echo "============================================================"
  print_r4r_processes
  print_ollama_processes
}

terminate_r4r_clients() {
  local pid round alive
  local -a pids=()
  local -a survivors=()

  # Varias rondas cubren hijos que aparezcan mientras cae el padre.
  for round in 1 2 3; do
    refresh_selection
    (( ${#selected[@]} > 0 )) || break

    mapfile -t pids < <(
      printf '%s\n' "${!selected[@]}" | sort -nr
    )

    echo
    echo "Ronda $round: SIGTERM a procesos R4R/OpenCode: ${pids[*]}"

    for pid in "${pids[@]}"; do
      kill -TERM "$pid" 2>/dev/null || true
    done

    for _ in {1..8}; do
      alive=0
      for pid in "${pids[@]}"; do
        if kill -0 "$pid" 2>/dev/null; then
          alive=1
          break
        fi
      done
      (( alive == 0 )) && break
      sleep 1
    done
  done

  refresh_selection

  if (( ${#selected[@]} > 0 )); then
    mapfile -t survivors < <(
      printf '%s\n' "${!selected[@]}" | sort -nr
    )

    echo
    echo "SIGKILL a supervivientes: ${survivors[*]}"

    for pid in "${survivors[@]}"; do
      kill -KILL "$pid" 2>/dev/null || true
    done

    sleep 2
  fi

  refresh_selection

  if (( ${#selected[@]} > 0 )); then
    echo "ERROR: siguen vivos procesos R4R/OpenCode:" >&2
    for pid in "${!selected[@]}"; do
      ps -ww -p "$pid" \
        -o pid=,ppid=,pgid=,etimes=,stat=,args= \
        2>/dev/null || true
    done | sort -n >&2
    return 1
  fi

  echo
  echo "Clientes R4R/OpenCode retirados."
}

restart_ollama() {
  local state=""

  echo
  echo "Reiniciando Ollama..."
  sudo systemctl restart ollama

  for _ in {1..20}; do
    state="$(systemctl is-active ollama 2>/dev/null || true)"
    [[ "$state" == "active" ]] && break
    sleep 1
  done

  if [[ "$state" != "active" ]]; then
    echo "ERROR: Ollama no quedó activo. Estado: ${state:-desconocido}" >&2
    sudo systemctl status ollama --no-pager -n 40 || true
    return 1
  fi

  echo "Ollama activo."
  echo "Esperando 7 segundos para detectar reconexiones huérfanas..."
  sleep 7
}

case "$MODE" in
  --list)
    show_snapshot "ESTADO ACTUAL"
    ;;

  --kill)
    show_snapshot "ANTES DE LA LIMPIEZA"
    terminate_r4r_clients
    show_snapshot "DESPUÉS DE LA LIMPIEZA"
    ;;

  --restart-ollama)
    show_snapshot "ANTES DE REINICIAR OLLAMA"
    restart_ollama
    show_snapshot "DESPUÉS DE REINICIAR OLLAMA"
    ;;

  --all)
    show_snapshot "1/3 — ESTADO INICIAL"
    terminate_r4r_clients
    restart_ollama
    show_snapshot "3/3 — COMPROBACIÓN FINAL"

    refresh_selection
    if (( ${#selected[@]} > 0 )); then
      echo
      echo "ERROR: reaparecieron clientes R4R/OpenCode." >&2
      exit 1
    fi

    echo
    echo "Limpieza completa: sin clientes R4R/OpenCode huérfanos y Ollama activo."
    ;;
esac
