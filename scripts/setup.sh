#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

[[ -f .env ]] || cp .env.example .env
for command in java javac mvn docker npm python3; do
  command -v "$command" >/dev/null 2>&1 || { echo "Missing required command: $command" >&2; exit 2; }
done
docker compose version >/dev/null
JAVA_VERSION="$(javac -version 2>&1)"
[[ "$JAVA_VERSION" == javac\ 21* ]] || { echo "Java 21 is required; found: $JAVA_VERSION" >&2; exit 2; }

mkdir -p docker-postgres/data/app docker-postgres/backups runtime/runs runtime/locks
npm --prefix .opencode install
python3 -m venv py-codex-agent/.venv
py-codex-agent/.venv/bin/python -m pip install -e py-codex-agent

if command -v codegraph >/dev/null 2>&1; then
  [[ -d .codegraph ]] || codegraph init
  codegraph sync . --quiet || echo "Warning: CodeGraph sync failed" >&2
else
  echo "Warning: CodeGraph is not installed; OpenCode can still run without it" >&2
fi

./scripts/db.sh up
./scripts/verify.sh unit

echo "Setup complete. Edit .env for machine-specific overrides, then run ./scripts/verify.sh all"
