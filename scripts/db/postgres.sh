#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
[[ -f .env ]] && set -a && source .env && set +a

command -v docker >/dev/null 2>&1 || {
  printf 'Docker is required for the default PostgreSQL runtime.\n' >&2
  exit 1
}

case "${1:-}" in
  up) docker compose -f infra/postgres/compose.yml up -d --wait ;;
  down) docker compose -f infra/postgres/compose.yml down ;;
  status) docker compose -f infra/postgres/compose.yml ps ;;
  logs) docker compose -f infra/postgres/compose.yml logs --tail 100 postgres ;;
  *) printf 'Usage: %s {up|down|status|logs}\n' "$0" >&2; exit 2 ;;
esac
