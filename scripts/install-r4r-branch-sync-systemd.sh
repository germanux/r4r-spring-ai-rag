#!/usr/bin/env bash
set -Eeuo pipefail

DEVELOPMENT_ROOT="${R4R_DEVELOPMENT_ROOT:-$HOME/Desarrollo}"
INTEGRATION_ROOT="${R4R_INTEGRATION_WORKTREE:-$DEVELOPMENT_ROOT/r4r-integration.git}"
INTERVAL="${R4R_BRANCH_SYNC_INTERVAL:-15min}"
UNIT_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
SERVICE="$UNIT_DIR/r4r-agent-branch-sync.service"
TIMER="$UNIT_DIR/r4r-agent-branch-sync.timer"
ACTION="${1:-install}"

usage() {
  cat <<'USAGE'
Usage: ./scripts/install-r4r-branch-sync-systemd.sh [install|uninstall]

Installs a user-level fallback timer. Every fifteen minutes, by default, it runs the
complete worktree-aware hot-sync pass with fetch, centralization, propagation,
pushes, dirty-state preservation and prior-runtime restoration.

Environment:
  R4R_DEVELOPMENT_ROOT
  R4R_INTEGRATION_WORKTREE
  R4R_BRANCH_SYNC_INTERVAL   default: 15min
USAGE
}

[[ "$ACTION" == install || "$ACTION" == uninstall || "$ACTION" == -h || "$ACTION" == --help ]] \
  || { usage >&2; exit 2; }
[[ "$ACTION" != -h && "$ACTION" != --help ]] || { usage; exit 0; }

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
OnActiveSec=$INTERVAL
OnUnitInactiveSec=$INTERVAL
AccuracySec=15s
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
systemctl --user enable --now r4r-agent-branch-sync.timer
systemctl --user reset-failed r4r-agent-branch-sync.service 2>/dev/null || true

echo "Installed: $SERVICE"
echo "Installed: $TIMER"
systemctl --user list-timers r4r-agent-branch-sync.timer --no-pager
