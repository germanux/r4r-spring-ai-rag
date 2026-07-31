#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="${R4R_REPO:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
ROOT="$(realpath "$ROOT")"
echo "[r4r] automatic dirty-worktree ownership recovery is disabled"
echo "[r4r] the controller now resumes task-scoped changes directly: $ROOT"
