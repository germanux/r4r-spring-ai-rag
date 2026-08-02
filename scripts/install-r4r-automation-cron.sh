#!/usr/bin/env bash
set -Eeuo pipefail

DEVELOPMENT_ROOT="${R4R_DEVELOPMENT_ROOT:-$HOME/Desarrollo}"
INTEGRATION_ROOT="${R4R_INTEGRATION_WORKTREE:-$DEVELOPMENT_ROOT/r4r-integration.git}"
LOG_FILE="${R4R_BRANCH_SYNC_LOG:-$DEVELOPMENT_ROOT/r4r-agent-branch-sync.log}"

[[ -x "$INTEGRATION_ROOT/scripts/sync-agent-branches.sh" ]] || {
  echo "ERROR: sync script not executable: $INTEGRATION_ROOT/scripts/sync-agent-branches.sh" >&2
  exit 2
}

existing="$(crontab -l 2>/dev/null || true)"
{
  printf '%s\n' "$existing" \
    | sed \
        -e '/R4R_AGENT_INTEGRATION/d' \
        -e '/R4R_AGENT_BRANCH_SYNC/d' \
        -e '/R4R_PHASE3_GUARDIAN/d'
  printf '%s\n' "* * * * * /usr/bin/flock -n /tmp/r4r-agent-branch-sync.cron.lock /bin/bash -lc 'cd $INTEGRATION_ROOT && ./scripts/sync-agent-branches.sh --push' >> $LOG_FILE 2>&1 # R4R_AGENT_BRANCH_SYNC"
} | awk 'NF || !blank++' | crontab -

echo "Installed one canonical R4R branch-sync cron entry."
crontab -l | grep -E 'R4R_(AGENT_INTEGRATION|AGENT_BRANCH_SYNC|PHASE3_GUARDIAN)' || true
