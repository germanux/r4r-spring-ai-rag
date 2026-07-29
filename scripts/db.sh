#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/.env"
COMPOSE_FILE="$ROOT/docker-postgres/compose.yml"

[[ -f "$ENV_FILE" ]] || { echo "Missing $ENV_FILE; run ./scripts/setup.sh first" >&2; exit 2; }
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
mkdir -p "$ROOT/docker-postgres/data/app" "$ROOT/docker-postgres/backups"

if docker info >/dev/null 2>&1; then
  DOCKER=(docker)
  DOCKER_PRIVILEGE=()
elif command -v sudo >/dev/null 2>&1 && sudo docker info >/dev/null 2>&1; then
  DOCKER=(sudo docker)
  DOCKER_PRIVILEGE=(sudo)
else
  echo "Docker is installed but the daemon is unavailable or access was denied." >&2
  echo "Run ./scripts/setup.sh, or start Docker and log out/in after being added to the docker group." >&2
  exit 2
fi

if "${DOCKER[@]}" compose version >/dev/null 2>&1; then
  COMPOSE=("${DOCKER[@]}" compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=("${DOCKER_PRIVILEGE[@]}" docker-compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
else
  echo "Docker Compose is not installed. Run ./scripts/setup.sh." >&2
  exit 2
fi

wait_healthy() {
  local service="$1" container status
  container="$("${COMPOSE[@]}" ps -q "$service")"
  [[ -n "$container" ]] || { echo "No container for $service" >&2; return 1; }
  for _ in $(seq 1 60); do
    status="$("${DOCKER[@]}" inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container")"
    [[ "$status" == "healthy" ]] && return 0
    [[ "$status" == "exited" || "$status" == "dead" ]] && break
    sleep 1
  done
  "${COMPOSE[@]}" logs "$service" >&2 || true
  echo "$service did not become healthy" >&2
  return 1
}

case "${1:-status}" in
  up)
    "${COMPOSE[@]}" up -d postgres-app
    wait_healthy postgres-app
    ;;
  down)
    "${COMPOSE[@]}" rm -sf postgres-app
    ;;
  status)
    "${COMPOSE[@]}" --profile test ps
    ;;
  logs)
    "${COMPOSE[@]}" logs -f postgres-app
    ;;
  reset)
    [[ "${2:-}" == "--yes" ]] || { echo "Use: $0 reset --yes" >&2; exit 2; }
    "${COMPOSE[@]}" rm -sf postgres-app || true
    "${DOCKER[@]}" run --rm --user root \
      -v "$ROOT/docker-postgres/data/app:/var/lib/postgresql/data" \
      "$POSTGRES_IMAGE" sh -c 'find /var/lib/postgresql/data -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +'
    touch "$ROOT/docker-postgres/data/app/.gitkeep"
    "${COMPOSE[@]}" up -d postgres-app
    wait_healthy postgres-app
    ;;
  test-up)
    "${COMPOSE[@]}" --profile test rm -sf postgres-test >/dev/null 2>&1 || true
    "${COMPOSE[@]}" --profile test up -d postgres-test
    wait_healthy postgres-test
    ;;
  test-down)
    "${COMPOSE[@]}" --profile test rm -sf postgres-test
    ;;
  test-logs)
    "${COMPOSE[@]}" --profile test logs -f postgres-test
    ;;
  *)
    echo "Usage: $0 {up|down|status|logs|reset --yes|test-up|test-down|test-logs}" >&2
    exit 2
    ;;
esac
