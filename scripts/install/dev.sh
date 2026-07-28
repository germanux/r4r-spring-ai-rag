#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

for command in java javac python3 node npm; do
  command -v "$command" >/dev/null 2>&1 || {
    printf 'Missing required command: %s\n' "$command" >&2
    exit 1
  }
done
command -v mvn >/dev/null 2>&1 || {
  printf 'Maven is required for Java validation. Install Maven and rerun.\n' >&2
  exit 1
}

if [[ ! -d .venv ]]; then
  python3 -m venv .venv
fi
.venv/bin/python -m pip install --upgrade pip
.venv/bin/python -m pip install -e tools/orchestrator
npm install --prefix .opencode

printf 'Development dependencies installed.\n'
