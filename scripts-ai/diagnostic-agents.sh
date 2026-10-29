cd ~/Desarrollo/r4r-ring-agent.git

OUT="$HOME/r4r-ring-bloqueo-$(date +%Y%m%d-%H%M%S).txt"

RING_PID="$(
  python3 -c \
    'import json; print(json.load(open("runtime/the-ring-heartbeats/RING.json"))["pid"])'
)"

{
  echo "===== FECHA ====="
  date -Is

  echo "===== RING PID ====="
  printf 'RING_PID=%s\n' "$RING_PID"

  echo "===== HEARTBEAT ====="
  cat runtime/the-ring-heartbeats/RING.json

  echo "===== PROCESO RING ====="
  ps -o pid,ppid,pgid,sid,stat,etime,wchan:32,args -p "$RING_PID"

  echo "===== HIJOS DIRECTOS ====="
  CHILD_PIDS="$(pgrep -P "$RING_PID" || true)"
  printf 'CHILD_PIDS=%s\n' "$CHILD_PIDS"

  for PID in $CHILD_PIDS
  do
    echo "--- PID $PID"
    ps -o pid,ppid,pgid,sid,stat,etime,wchan:32,args -p "$PID"

    echo "cmdline:"
    tr '\0' ' ' <"/proc/$PID/cmdline"
    echo

    echo "status:"
    grep -E '^(Name|State|Pid|PPid|Threads):' "/proc/$PID/status"

    echo "wchan:"
    cat "/proc/$PID/wchan"
    echo

    echo "subprocesos:"
    pgrep -P "$PID" -a || true
  done

  echo "===== PROCESOS OPENCODE/NODE/BUN ====="
  ps -eo pid,ppid,pgid,sid,stat,etime,wchan:28,args --forest |
    grep -E 'PID|run-ring-agent|opencode|/bun| node ' |
    grep -v grep

  echo "===== TAMAÑO Y FECHA DE LOGS ====="
  stat -c '%y | %s bytes | %n' \
    runtime/ring-system/ring-agent.console.log \
    runtime/ring-system/supervisor.log \
    runtime/ring-agent/ring/20260807T204433Z/opencode.console.log

  echo "===== CONSOLA RING ====="
  tail -n 200 runtime/ring-system/ring-agent.console.log

  echo "===== SUPERVISOR ====="
  tail -n 200 runtime/ring-system/supervisor.log

  echo "===== DETENCIÓN ====="
  ./scripts/run-ring-system.sh stop

  echo "===== ESTADO FINAL ====="
  ./scripts/run-ring-system.sh status

  echo "===== PROCESOS RESTANTES ====="
  pgrep -af 'run-ring-agent.py|opencode|run-opencode-worker' || true
} >"$OUT" 2>&1

printf 'Diagnóstico: %s\n' "$OUT"
