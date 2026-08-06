#!/usr/bin/env bash
set -Eeuo pipefail

artifact_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository="${1:-$HOME/Desarrollo/r4r-integration.git}"
full_patch="$artifact_dir/r4r-stop-git-churn-hourly-sync.patch"
incremental_patch="$artifact_dir/r4r-stop-git-churn-hourly-sync-after-surgical.patch"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 2
}

[[ -d "$repository" ]] || fail "repository not found: $repository"
git -C "$repository" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || fail "not a Git worktree: $repository"
[[ "$(git -C "$repository" branch --show-current)" == "agent/integration" ]] \
  || fail "run this only on the agent/integration worktree"
git -C "$repository" rev-parse -q --verify MERGE_HEAD >/dev/null 2>&1 \
  && fail "a merge is pending; abort or resolve it first"
[[ -f "$full_patch" && -f "$incremental_patch" ]] \
  || fail "place this script beside both patch files"

selected=""
if git -C "$repository" apply --check "$full_patch" 2>/dev/null; then
  selected="$full_patch"
elif git -C "$repository" apply --check "$incremental_patch" 2>/dev/null; then
  selected="$incremental_patch"
else
  printf '%s\n' "Neither patch matches the current worktree:" >&2
  git -C "$repository" status --short >&2
  fail "no files were changed"
fi

git -C "$repository" apply "$selected"
git -C "$repository" diff --check

printf 'Applied: %s\n' "$(basename "$selected")"
printf '%s\n' 'No commit or push was performed. Review these changes:'
git -C "$repository" status --short
