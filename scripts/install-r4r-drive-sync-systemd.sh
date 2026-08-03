#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_SCRIPT="$ROOT/scripts/r4r-drive-import-safe.py"
INSTALL_DIR="$HOME/.local/lib/r4r"
INSTALL_SCRIPT="$INSTALL_DIR/r4r-drive-import-safe.py"
UNIT_DIR="$HOME/.config/systemd/user"
SERVICE="$UNIT_DIR/r4r-drive-import-safe.service"
TIMER="$UNIT_DIR/r4r-drive-import-safe.timer"
DEVELOPMENT_ROOT="${R4R_DEVELOPMENT_ROOT:-$HOME/Desarrollo}"
INSYNC_ROOT="${R4R_INSYNC_ROOT:-$HOME/Insync/riansares4r@gmail.com/Google Drive/Agentes R4R/r4r-ring-agent.git}"
DESTINATION="${R4R_GOOGLE_DRIVE_WORKTREE:-$DEVELOPMENT_ROOT/r4r-google-drive.git}"
MANIFEST="${R4R_DRIVE_SYNC_MANIFEST:-$DEVELOPMENT_ROOT/.r4r-runtime/drive-import/state.json}"
GIT_LOCK="${R4R_GIT_LOCK:-$DEVELOPMENT_ROOT/.r4r-runtime/git.lock}"
ACTION="${1:-install}"

usage() {
  cat <<'USAGE'
Usage: ./scripts/install-r4r-drive-sync-systemd.sh [install|uninstall]

Installs the conflict-aware Insync <-> agent/r4r-google-drive bridge. The
service keeps the existing unit name for an in-place upgrade.
USAGE
}

[[ "$ACTION" == install || "$ACTION" == uninstall || "$ACTION" == -h || "$ACTION" == --help ]] \
  || { usage >&2; exit 2; }
[[ "$ACTION" != -h && "$ACTION" != --help ]] || { usage; exit 0; }

if [[ "$ACTION" == uninstall ]]; then
  systemctl --user disable --now r4r-drive-import-safe.timer 2>/dev/null || true
  systemctl --user stop r4r-drive-import-safe.service 2>/dev/null || true
  rm -f "$SERVICE" "$TIMER"
  systemctl --user daemon-reload
  echo "Removed r4r-drive-import-safe systemd units."
  exit 0
fi

[[ -f "$SOURCE_SCRIPT" ]] || { echo "ERROR: missing $SOURCE_SCRIPT" >&2; exit 2; }
[[ -d "$INSYNC_ROOT" ]] || { echo "ERROR: missing Insync directory: $INSYNC_ROOT" >&2; exit 2; }
git -C "$DESTINATION" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || { echo "ERROR: not a Git worktree: $DESTINATION" >&2; exit 2; }

mkdir -p "$INSTALL_DIR" "$UNIT_DIR" "$(dirname "$MANIFEST")" "$(dirname "$GIT_LOCK")"
install -m 0755 "$SOURCE_SCRIPT" "$INSTALL_SCRIPT"

cat >"$SERVICE" <<EOF
[Unit]
Description=Safely synchronize R4R Google Drive and agent/r4r-google-drive
After=graphical-session.target network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/bin/python3 "$INSTALL_SCRIPT" --source "$INSYNC_ROOT" --destination "$DESTINATION" --manifest "$MANIFEST" --lock "$GIT_LOCK" --lock-timeout 55 --bidirectional --commit --push
TimeoutStartSec=10min
Nice=10
IOSchedulingClass=best-effort
IOSchedulingPriority=6
UMask=0022

[Install]
WantedBy=default.target
EOF

cat >"$TIMER" <<'EOF'
[Unit]
Description=Run safe R4R Google Drive synchronization every 1min

[Timer]
OnBootSec=1min
OnUnitActiveSec=1min
AccuracySec=10s
Persistent=true
Unit=r4r-drive-import-safe.service

[Install]
WantedBy=timers.target
EOF

systemctl --user daemon-reload
systemctl --user enable --now r4r-drive-import-safe.timer
systemctl --user reset-failed r4r-drive-import-safe.service 2>/dev/null || true
echo "Installed: $INSTALL_SCRIPT"
echo "Installed: $SERVICE"
echo "Installed: $TIMER"
systemctl --user list-timers r4r-drive-import-safe.timer --no-pager
