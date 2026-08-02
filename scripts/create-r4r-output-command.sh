mkdir -p ~/.local/bin

cat > ~/.local/bin/r4r-output <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

CURRENT_PATH="$(pwd -P)"
CURRENT_PATH_LOWER="${CURRENT_PATH,,}"

RING_ROOT="${R4R_RING_WORKTREE:-$HOME/Desarrollo/r4r-ring-agent.git}"

case "$CURRENT_PATH_LOWER" in
  *r4r-lp-worker.git*|*r4r-ring-lp.git*|*laptop*)
    WORKER="LP"
    WORKER_KEY="lp"
    WORKER_ROOT="$HOME/Desarrollo/r4r-lp-worker.git"
    ;;

  *r4r-pc-worker.git*|*pc-worker*)
    WORKER="PC"
    WORKER_KEY="pc"
    WORKER_ROOT="$HOME/Desarrollo/r4r-pc-worker.git"
    ;;

  *)
    printf 'ERROR: no puedo identificar PC o LP desde esta ruta:\n  %s\n' \
      "$CURRENT_PATH" >&2
    printf '\nEjecuta el comando dentro de:\n' >&2
    printf '  ~/Desarrollo/r4r-pc-worker.git\n' >&2
    printf '  ~/Desarrollo/r4r-lp-worker.git\n' >&2
    exit 2
    ;;
esac

LATEST_LOG="$(
  find \
    "$RING_ROOT/runtime/ring-agent/$WORKER_KEY" \
    "$WORKER_ROOT/runtime/runs/$WORKER" \
    -type f \
    \( \
      -name 'controller.console.log' \
      -o -name '*controller*.log' \
    \) \
    -printf '%T@ %p\n' 2>/dev/null |
  sort -nr |
  head -1 |
  cut -d' ' -f2-
)"

printf 'Worker:     %s\n' "$WORKER"
printf 'Directorio: %s\n  sort -nr |
  head -1 |
  cut -d' ' -f2-
)"

printf '' "$CURRENT_PATH"

if [[ -z "$LATEST_LOG" || ! -f "$LATEST_LOG" ]]; then
  printf 'ERROR: no encontré ningún log del controlador %s.\n' "$WORKER" >&2
  exit 3
fi

printf 'Log:        %s\n\n' "$LATEST_LOG"

case "${1:-}" in
  --once|-n)
    tail -n "${R4R_OUTPUT_LINES:-120}" "$LATEST_LOG"
    ;;

  --path)
    printf '%s\n' "$LATEST_LOG"
    ;;

  "")
    exec tail -n "${R4R_OUTPUT_LINES:-120}" -F "$LATEST_LOG"
    ;;

  *)
    printf 'Uso:\n' >&2
    printf '  r4r-output          Seguir el log en directo\n' >&2
    printf '  r4r-output --once   Mostrar las últimas líneas y salir\n' >&2
    printf '  r4r-output --path   Mostrar solamente la ruta del log\n' >&2
    exit 2
    ;;
esac
EOF

chmod +x ~/.local/bin/r4r-output
