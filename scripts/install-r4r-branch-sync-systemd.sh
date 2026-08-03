#!/usr/bin/env bash
set -Eeuo pipefail

DEVELOPMENT_ROOT="${R4R_DEVELOPMENT_ROOT:-$HOME/Desarrollo}"
INTEGRATION_ROOT="${R4R_INTEGRATION_WORKTREE:-$DEVELOPMENT_ROOT/r4r-integration.git}"
RING_ROOT="${R4R_RING_WORKTREE:-$DEVELOPMENT_ROOT/r4r-ring-agent.git}"
INTERVAL="${R4R_BRANCH_SYNC_INTERVAL:-3min}"
UNIT_DIR="$HOME/.config/systemd/user"
SERVICE="$UNIT_DIR/r4r-agent-branch-sync.service"
TIMER="$UNIT_DIR/r4r-agent-branch-sync.timer"

usage() {
  cat <<'USAGE'
Usage: ./scripts/install-r4r-branch-sync-systemd.sh [--uninstall]

Installs a user-level systemd timer that every 3 minutes:
  - collects all active agent branches into agent/integration;
  - propagates the pinned integration commit to all branches;
  - pushes integration and updated branches;
  - displays a GNOME/KDE notification and opens the exact worktree on conflict.

Environment overrides:
  R4R_DEVELOPMENT_ROOT
  R4R_INTEGRATION_WORKTREE
  R4R_RING_WORKTREE
  R4R_BRANCH_SYNC_INTERVAL   default: 3min
USAGE
}

if [[ "${1:-}" == '--uninstall' ]]; then
  systemctl --user disable --now r4r-agent-branch-sync.timer 2>/dev/null || true
  systemctl --user stop r4r-agent-branch-sync.service 2>/dev/null || true
  rm -f "$SERVICE" "$TIMER"
  systemctl --user daemon-reload
  echo 'Removed r4r-agent-branch-sync user timer.'
  exit 0
elif [[ $# -gt 0 ]]; then
  usage >&2
  exit 2
fi

[[ -x "$INTEGRATION_ROOT/scripts/sync-agent-branches.sh" ]] || {
  echo "ERROR: sync script not executable: $INTEGRATION_ROOT/scripts/sync-agent-branches.sh" >&2
  exit 2
}

git -C "$INTEGRATION_ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1 || {
  echo "ERROR: invalid integration worktree: $INTEGRATION_ROOT" >&2
  exit 2
}

mkdir -p "$UNIT_DIR"

cat > "$SERVICE" <<UNIT
[Unit]
Description=R4R collect branches into integration and propagate them
Wants=network-online.target
After=network-online.target graphical-session.target

[Service]
Type=oneshot
WorkingDirectory=$INTEGRATION_ROOT
Environment=R4R_DEVELOPMENT_ROOT=$DEVELOPMENT_ROOT
Environment=R4R_INTEGRATION_WORKTREE=$INTEGRATION_ROOT
Environment=R4R_RING_WORKTREE=$RING_ROOT
ExecStart=/bin/bash -lc 'exec ./scripts/sync-agent-branches.sh --source agent/integration --fetch --push'
TimeoutStartSec=25min
Nice=10
IOSchedulingClass=idle

[Install]
WantedBy=default.target
UNIT

cat > "$TIMER" <<UNIT
[Unit]
Description=Run R4R hub branch synchronization every $INTERVAL

[Timer]
OnBootSec=45s
OnUnitActiveSec=$INTERVAL
AccuracySec=5s
Persistent=true
Unit=r4r-agent-branch-sync.service

[Install]
WantedBy=timers.target
UNIT

# Remove only the legacy branch-sync cron entry to avoid duplicate executions.
if command -v crontab >/dev/null 2>&1; then
  existing="$(crontab -l 2>/dev/null || true)"
  printf '%s\n' "$existing" | sed '/R4R_AGENT_BRANCH_SYNC/d' | crontab -
fi

# Make desktop notification variables visible to the user manager when possible.
systemctl --user import-environment \
  DISPLAY WAYLAND_DISPLAY DBUS_SESSION_BUS_ADDRESS XAUTHORITY 2>/dev/null || true
systemctl --user daemon-reload
systemctl --user enable --now r4r-agent-branch-sync.timer

echo
systemctl --user status r4r-agent-branch-sync.timer --no-pager || true
echo
systemctl --user list-timers r4r-agent-branch-sync.timer --no-pager || true
cat <<EOF2

Installed:
  $SERVICE
  $TIMER

Manual run:
  systemctl --user start r4r-agent-branch-sync.service

Logs:
  journalctl --user -u r4r-agent-branch-sync.service -f

Disable:
  systemctl --user disable --now r4r-agent-branch-sync.timer
EOF2
