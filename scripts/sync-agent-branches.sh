#!/usr/bin/env bash
set -Eeuo pipefail

REPO="/home/german/Desarrollo/r4r-ring-agent.git"
INTEGRATION="/home/german/Desarrollo/r4r-integration.git"
REMOTE="origin"

exec 8>/tmp/r4r-drive-import.lock
/usr/bin/flock -x 8

echo "=== $(date --iso-8601=seconds) ==="

git -C "$REPO" fetch "$REMOTE" --prune

mapfile -t LOCAL_BRANCHES < <(
  git -C "$REPO" for-each-ref \
    --format='%(refname:short)' 'refs/heads/agent/*' |
    grep -vx 'agent/integration' |
    LC_ALL=C sort || true
)

for branch in "${LOCAL_BRANCHES[@]}"; do
  echo "Push: $branch"
  git -C "$REPO" push "$REMOTE" \
    "refs/heads/$branch:refs/heads/$branch"
done

git -C "$REPO" fetch "$REMOTE" --prune

if [[ -n "$(git -C "$INTEGRATION" status --porcelain)" ]]; then
  echo "ERROR: agent/integration tiene cambios sin confirmar."
  exit 1
fi

if git -C "$REPO" show-ref --verify --quiet \
  refs/remotes/origin/agent/integration; then
  git -C "$INTEGRATION" merge --no-edit origin/agent/integration || {
    git -C "$INTEGRATION" merge --abort || true
    echo "CONFLICTO con origin/agent/integration"
    exit 2
  }
fi

mapfile -t REMOTE_BRANCHES < <(
  git -C "$REPO" for-each-ref \
    --format='%(refname:short)' 'refs/remotes/origin/agent/*' |
    grep -vx 'origin/agent/integration' |
    LC_ALL=C sort || true
)

for branch in "${REMOTE_BRANCHES[@]}"; do
  if git -C "$INTEGRATION" merge-base --is-ancestor \
    "$branch" HEAD; then
    echo "Sin cambios: $branch"
    continue
  fi

  echo "Merge: $branch"

  if ! git -C "$INTEGRATION" merge \
    --no-ff --no-edit "$branch"; then
    git -C "$INTEGRATION" merge --abort || true
    echo "CONFLICTO: $branch. No se publica integration."
    exit 2
  fi
done

git -C "$INTEGRATION" push "$REMOTE" \
  refs/heads/agent/integration:refs/heads/agent/integration

echo "Integración terminada correctamente."
