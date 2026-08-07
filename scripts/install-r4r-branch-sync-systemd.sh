#!/usr/bin/env bash
set -Eeuo pipefail

DEVELOPMENT_ROOT="${R4R_DEVELOPMENT_ROOT:-$HOME/Desarrollo}"
INTEGRATION_ROOT="${R4R_INTEGRATION_WORKTREE:-$DEVELOPMENT_ROOT/r4r-integration.git}"
INTERVAL="${R4R_BRANCH_SYNC_INTERVAL:-1h}"
INITIAL_DELAY="${R4R_BRANCH_SYNC_INITIAL_DELAY:-2min}"
UNIT_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
SERVICE="$UNIT_DIR/r4r-agent-branch-sync.service"
TIMER="$UNIT_DIR/r4r-agent-branch-sync.timer"
ACTION="${1:-install}"

usage() {
  cat <<'USAGE'
Usage: ./scripts/install-r4r-branch-sync-systemd.sh [install|status|uninstall]

Installs a user-level fallback timer. It performs one synchronous verification pass
at installation, waits two minutes after a later timer activation, and then runs the
complete worktree-aware hot-sync pass once per hour by default.

Environment:
  R4R_DEVELOPMENT_ROOT
  R4R_INTEGRATION_WORKTREE
  R4R_BRANCH_SYNC_INTERVAL        default: 1h
  R4R_BRANCH_SYNC_INITIAL_DELAY   default: 2min
USAGE
}

[[ "$ACTION" == install || "$ACTION" == status || "$ACTION" == uninstall || "$ACTION" == -h || "$ACTION" == --help ]] \
  || { usage >&2; exit 2; }
[[ "$ACTION" != -h && "$ACTION" != --help ]] || { usage; exit 0; }

if [[ "$ACTION" == status ]]; then
  echo "Timer enabled:"
  systemctl --user is-enabled r4r-agent-branch-sync.timer 2>/dev/null || true
  echo "Timer/service state:"
  systemctl --user show \
    r4r-agent-branch-sync.timer \
    r4r-agent-branch-sync.service \
    -p Id -p ActiveState -p SubState -p Result -p ExecMainStatus \
    --no-pager 2>/dev/null || true
  echo "Schedule:"
  systemctl --user list-timers r4r-agent-branch-sync.timer --all --no-pager \
    2>/dev/null || true
  echo "Recent service log:"
  if command -v journalctl >/dev/null 2>&1; then
    journalctl --user -u r4r-agent-branch-sync.service -n 80 --no-pager || true
  else
    echo "journalctl unavailable"
  fi
  exit 0
fi

if [[ "$ACTION" == uninstall ]]; then
  systemctl --user disable --now r4r-agent-branch-sync.timer 2>/dev/null || true
  systemctl --user stop r4r-agent-branch-sync.service 2>/dev/null || true
  rm -f "$SERVICE" "$TIMER"
  systemctl --user daemon-reload
  echo "R4R branch-sync timer removed."
  exit 0
fi

INTEGRATION_ROOT="$(realpath -e "$INTEGRATION_ROOT" 2>/dev/null)" || {
  echo "ERROR: integration worktree not found: $INTEGRATION_ROOT" >&2
  exit 2
}
SCRIPT="$INTEGRATION_ROOT/scripts/sync-agent-branches.sh"
[[ -x "$SCRIPT" ]] || { echo "ERROR: missing executable $SCRIPT" >&2; exit 2; }

mkdir -p "$UNIT_DIR"
cat >"$SERVICE" <<EOF
[Unit]
Description=R4R hot-sync all Git worktrees through agent/integration
After=network-online.target graphical-session.target
Wants=network-online.target

[Service]
Type=oneshot
WorkingDirectory=$INTEGRATION_ROOT
ExecStart=$SCRIPT
TimeoutStartSec=30min
Nice=10
IOSchedulingClass=best-effort
IOSchedulingPriority=6
Environment=R4R_DEVELOPMENT_ROOT=$DEVELOPMENT_ROOT
Environment=R4R_INTEGRATION_WORKTREE=$INTEGRATION_ROOT

[Install]
WantedBy=default.target
EOF

cat >"$TIMER" <<EOF
[Unit]
Description=Run R4R hot-sync every $INTERVAL

[Timer]
OnActiveSec=$INITIAL_DELAY
OnUnitInactiveSec=$INTERVAL
AccuracySec=1min
RandomizedDelaySec=2min
Persistent=false
Unit=r4r-agent-branch-sync.service

[Install]
WantedBy=timers.target
EOF

# An older installation created this drop-in with a three-minute cadence. It
# overrides the freshly written timer unless it is removed explicitly.
LEGACY_DROPIN="$UNIT_DIR/r4r-agent-branch-sync.timer.d/schedule.conf"
if [[ -f "$LEGACY_DROPIN" ]]; then
  rm -f "$LEGACY_DROPIN"
  rmdir "$(dirname "$LEGACY_DROPIN")" 2>/dev/null || true
  echo "Removed stale timer override: $LEGACY_DROPIN"
fi

# Remove only the legacy marked cron entry, avoiding duplicate schedulers.
if command -v crontab >/dev/null 2>&1; then
  current="$(crontab -l 2>/dev/null || true)"
  filtered="$(printf '%s\n' "$current" | grep -v 'R4R_AGENT_BRANCH_SYNC' || true)"
  [[ "$filtered" == "$current" ]] || printf '%s\n' "$filtered" | crontab -
fi

systemctl --user daemon-reload
systemctl --user reset-failed r4r-agent-branch-sync.service 2>/dev/null || true
systemctl --user enable --now r4r-agent-branch-sync.timer

# Run once synchronously so an inactive timer, dirty hub or authentication failure
# is reported during installation instead of looking like a silent scheduler stall.
if ! systemctl --user start r4r-agent-branch-sync.service; then
  echo "ERROR: initial R4R branch synchronization failed." >&2
  echo "Inspect with: $0 status" >&2
  systemctl --user status r4r-agent-branch-sync.service --no-pager >&2 || true
  exit 1
fi

echo "Installed: $SERVICE"
echo "Installed: $TIMER"
echo "Interval: $INTERVAL (initial delay after activation: $INITIAL_DELAY)"
systemctl --user is-enabled r4r-agent-branch-sync.timer
systemctl --user is-active r4r-agent-branch-sync.timer
systemctl --user list-timers r4r-agent-branch-sync.timer --all --no-pager
