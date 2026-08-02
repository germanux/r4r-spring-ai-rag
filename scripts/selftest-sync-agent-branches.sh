#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SYNC="$ROOT/scripts/sync-agent-branches.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/repo/scripts"
cp "$SYNC" "$TMP/repo/scripts/"
cd "$TMP/repo"
git init -q
git config user.name "R4R Selftest"
git config user.email "selftest@example.invalid"
printf 'base\n' > state.txt
git add .
git commit -qm base
git branch -m main

git branch agent/clean
git checkout -qb agent/conflict
printf 'target\n' > state.txt
git commit -qam target
conflict_before="$(git rev-parse HEAD)"
git checkout -q main
git branch r4r-chatgpt
git checkout -qb agent/integration
printf 'integration\n' > state.txt
git commit -qam integration

set +e
./scripts/sync-agent-branches.sh --no-workers --no-guardian --no-push >"$TMP/result.log" 2>&1
result=$?
set -e
[[ "$result" == 3 ]] || { cat "$TMP/result.log"; echo "Expected conflict exit 3, got $result" >&2; exit 1; }
git merge-base --is-ancestor agent/integration agent/clean
git merge-base --is-ancestor agent/integration r4r-chatgpt
[[ "$(git rev-parse agent/conflict)" == "$conflict_before" ]]
grep -Fq 'agent/conflict:conflict' "$TMP/result.log"
printf 'OK: integration fan-out and conflict isolation self-test passed\n'
