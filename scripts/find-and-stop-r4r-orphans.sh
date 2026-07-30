#!/usr/bin/env bash
set -Eeuo pipefail

REPO="${R4R_REPO:-/home/german/Desarrollo/r4r-spring-ai-rag.git}"
MODE="${1:---list}"
SELF_PID="$$"

usage() {
  cat <<'EOF'
Uso:
  ./find-and-stop-r4r-orphans.sh --list
  ./find-and-stop-r4r-orphans.sh --kill
  ./find-and-stop-r4r-orphans.sh --kill-and-restart-ollama

Variables:
  R4R_REPO=/ruta/al/repositorio

Comportamiento:
  --list
      Muestra controladores, OpenCode y descendientes asociados al repositorio.
  --kill
      Envía TERM; espera; después usa KILL solo para procesos supervivientes.
  --kill-and-restart-ollama
      Hace lo anterior y reinicia el servicio Ollama al final.

No mata Maven, PostgreSQL, el IDE ni procesos ajenos al repositorio.
EOF
}

case "$MODE" in
  --list|--kill|--kill-and-restart-ollama) ;;
  -h|--help)
    usage
    exit 0
    ;;
  *)
    echo "Modo no reconocido: $MODE" >&2
    usage >&2
    exit 2
    ;;
esac

if [[ ! -d "$REPO" ]]; then
  echo "No existe el repositorio: $REPO" >&2
  exit 2
fi

declare -A selected=()

is_r4r_agent_process() {
  local pid="$1"
  local cmd cwd

  [[ -r "/proc/$pid/cmdline" ]] || return 1
  cmd="$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)"
  cwd="$(readlink -f "/proc/$pid/cwd" 2>/dev/null || true)"

  # Excluir este mismo script.
  [[ "$pid" != "$SELF_PID" ]] || return 1

  # Debe pertenecer al repositorio por argumento o directorio de trabajo.
  if [[ "$cmd" != *"$REPO"* && "$cwd" != "$REPO"* ]]; then
    return 1
  fi

  # Procesos controladores/clientes que pueden sobrevivir a Ctrl+C.
  [[ "$cmd" =~ r4r_codex_agent|run-codex-agent|opencode ]]
}

# Raíces directamente relacionadas con este repositorio.
for proc in /proc/[0-9]*; do
  pid="${proc##*/}"
  if is_r4r_agent_process "$pid"; then
    selected["$pid"]=1
  fi
done

# Añadir descendientes de forma transitiva.
changed=1
while (( changed )); do
  changed=0
  while read -r pid ppid; do
    [[ -n "${selected[$ppid]:-}" ]] || continue
    [[ "$pid" != "$SELF_PID" ]] || continue
    if [[ -z "${selected[$pid]:-}" ]]; then
      selected["$pid"]=1
      changed=1
    fi
  done < <(ps -e -o pid=,ppid=)
done

print_selected() {
  if (( ${#selected[@]} == 0 )); then
    echo "No hay procesos R4R/OpenCode asociados a:"
    echo "  $REPO"
    return 0
  fi

  echo "Procesos R4R/OpenCode asociados a:"
  echo "  $REPO"
  echo
  printf '%-8s %-8s %-8s %-9s %-6s %s\n' PID PPID PGID ELAPSED STAT COMMAND

  for pid in "${!selected[@]}"; do
    ps -ww -p "$pid" -o pid=,ppid=,pgid=,etimes=,stat=,args= 2>/dev/null || true
  done | sort -n
}

print_model_processes() {
  echo
  echo "Procesos de Ollama/modelo visibles (solo información):"
  ps -ww -eo pid=,ppid=,pgid=,etimes=,stat=,args= \
    | grep -E '[o]llama( serve)?|[l]lama-server' \
    || echo "  ninguno"
}

print_selected
print_model_processes

[[ "$MODE" != "--list" ]] || exit 0

if (( ${#selected[@]} == 0 )); then
  if [[ "$MODE" == "--kill-and-restart-ollama" ]]; then
    echo
    echo "Reiniciando Ollama..."
    sudo systemctl restart ollama
  fi
  exit 0
fi

echo
echo "Enviando SIGTERM a procesos R4R/OpenCode..."
# Procesar primero PIDs altos suele retirar antes a los hijos.
mapfile -t pids < <(printf '%s\n' "${!selected[@]}" | sort -nr)

for pid in "${pids[@]}"; do
  kill -TERM "$pid" 2>/dev/null || true
done

for _ in {1..10}; do
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

survivors=()
for pid in "${pids[@]}"; do
  if kill -0 "$pid" 2>/dev/null; then
    survivors+=("$pid")
  fi
done

if (( ${#survivors[@]} > 0 )); then
  echo "Procesos supervivientes; enviando SIGKILL: ${survivors[*]}"
  for pid in "${survivors[@]}"; do
    kill -KILL "$pid" 2>/dev/null || true
  done
  sleep 1
fi

if [[ "$MODE" == "--kill-and-restart-ollama" ]]; then
  echo
  echo "Reiniciando Ollama después de retirar los clientes..."
  sudo systemctl restart ollama
  sleep 2
fi

echo
echo "Comprobación final:"
exec "$0" --list
