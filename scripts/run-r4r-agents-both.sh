#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p runtime/supervisor
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
pc_log="runtime/supervisor/${stamp}-PC.log"
lp_log="runtime/supervisor/${stamp}-LP.log"
./scripts/run-codex-agent.sh --destination PC > >(tee "$pc_log") 2>&1 & pc_pid=$!
./scripts/run-codex-agent.sh --destination LP > >(tee "$lp_log") 2>&1 & lp_pid=$!
printf 'PC pid=%s log=%s
LP pid=%s log=%s
' "$pc_pid" "$pc_log" "$lp_pid" "$lp_log"
trap 'kill "$pc_pid" "$lp_pid" 2>/dev/null || true' INT TERM
set +e
wait "$pc_pid"; pc_status=$?
wait "$lp_pid"; lp_status=$?
set -e
printf 'PC exit=%s; LP exit=%s
' "$pc_status" "$lp_status"
(( pc_status == 0 && lp_status == 0 ))
